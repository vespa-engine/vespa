// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iaccelerated.h"

#include "fn_table.h"
#include "functions.h"
#include "highway.h"
#ifdef __x86_64__
#define VESPA_HWACCEL_ARCH_NAME "x86-64"
#include "avx2.h"
#include "avx3.h"
#include "avx3_dl.h"
#include "x64_generic.h"
#else // aarch64
#define VESPA_HWACCEL_ARCH_NAME "aarch64"
#include "neon.h"
#include "neon_fp16_dotprod.h"
#include "sve.h"
#include "sve2.h"
// There's no __builtin_cpu_supports() on aarch64, so we have to go via getauxval()
// HW capability bits, _iff_ it exists on the platform. Otherwise we just have to
// assume that the default baseline build settings are OK.
#if defined(__has_include)
#if __has_include(<asm/hwcap.h>)
#define VESPA_HAS_AARCH64_HWCAP_H
#endif
#endif
#endif
#include <vespa/vespalib/util/memory.h>

#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <format>
#include <ranges>
#include <string>
#include <vector>

#ifdef VESPA_HAS_AARCH64_HWCAP_H
#include <asm/hwcap.h> // AT_HWCAP / AT_HWCAP2 definitions
#include <sys/auxv.h>  // getauxval()
#endif

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.hwaccelerated");

namespace vespalib::hwaccelerated {

using dispatch::FnTable;

namespace {

#ifdef __x86_64__

[[nodiscard]] bool supports_avx2_target() noexcept {
    // TODO should this also check for BMI2, F16 and FMA?
    return __builtin_cpu_supports("avx2");
}

// AVX3 is ~Skylake with AVX512{F, VL, DQ, BW, CD}
[[nodiscard]] bool supports_avx3_target() noexcept {
    // TODO should this check for "x86-64-v4" instead? v4 corresponds to Skylake
    return (__builtin_cpu_supports("avx512f") && __builtin_cpu_supports("avx512vl") &&
            __builtin_cpu_supports("avx512dq") && __builtin_cpu_supports("avx512bw") &&
            __builtin_cpu_supports("avx512cd"));
}

// AVX3_DL corresponds to Icelake Server(-ish). See `avx3_dl.h` for list of required CPU
// features. We check as many of those features as possible here (everything except VAES).
// There's currently no "x86-64-vN" alias with an N high enough to cover this target,
// so we have to do things the hard way.
[[nodiscard]] bool supports_avx3_dl_target() noexcept {
    return (supports_avx3_target() && __builtin_cpu_supports("avx512vnni") && __builtin_cpu_supports("vpclmulqdq") &&
            __builtin_cpu_supports("avx512vbmi") && __builtin_cpu_supports("avx512vbmi2") &&
            __builtin_cpu_supports("avx512vpopcntdq") && __builtin_cpu_supports("avx512bitalg") &&
            __builtin_cpu_supports("gfni"));
}

#else // aarch64

// Note: this does _not_ correspond to a Highway target! Highway has NEON and NEON_BF16
// at the "low end" of aarch64, where the latter implies SDOT/UDOT support. However, we
// have historically compiled against an ARM NEON baseline with `fp16+dotprod+crypto`
// but _not_ requiring BF16. To avoid breaking things, carry this forward. This also
// means we probably need some cleverness when integrating with Highway, although it might
// not break anything except Mac M1 i8 dot product performance in practice, since it has
// SDOT/UDOT but not BF16...
[[nodiscard]] bool supports_neon_aes_fp16_and_dotprod() {
#ifdef VESPA_HAS_AARCH64_HWCAP_H
    // Want to check for `fp16+dotprod+crypto` support:
    // HWCAP_AES     (ID_AA64ISAR0_EL1.AES)    ==> AES (crypto) support
    // HWCAP_ASIMDHP (ID_AA64PFR0_EL1.AdvSIMD) ==> fp16 support
    // HWCAP_ASIMDDP (ID_AA64ISAR0_EL1.DP)     ==> dotproduct support
#if defined(HWCAP_AES) && defined(HWCAP_ASIMDHP) && defined(HWCAP_ASIMDDP)
    const unsigned long hw = getauxval(AT_HWCAP);
    const bool          has_aes = (hw & HWCAP_AES) != 0;
    const bool          has_fp16 = (hw & HWCAP_ASIMDHP) != 0;
    const bool          has_dotprod = (hw & HWCAP_ASIMDDP) != 0;
    return (has_aes && has_fp16 && has_dotprod);
#else
    return false;
#endif
#else
    return false;
#endif // VESPA_HAS_AARCH64_HWCAP_H
}

// Support for Scalable Vector Extension
[[nodiscard]] bool supports_sve() {
#if defined(HWCAP_SVE)
    if (!supports_neon_aes_fp16_and_dotprod()) {
        return false;
    }
    const unsigned long hw = getauxval(AT_HWCAP);
    const bool          has_sve = (hw & HWCAP_SVE) != 0;
    return has_sve;
#else
    return false;
#endif
}

// Support for Scalable Vector Extension 2
[[nodiscard]] bool supports_sve2() {
#if defined(HWCAP2_SVE2)
    if (!supports_sve()) {
        return false;
    }
    const unsigned long hw = getauxval(AT_HWCAP2);
    const bool          has_sve2 = (hw & HWCAP2_SVE2) != 0;
    return has_sve2;
#else
    return false;
#endif
}

#endif // aarch64

namespace target {

// This is a placeholder until we integrate with Google Highway's target API.
// Instead of having a _set_ of targets, we simplify to just have a target _level_,
// where all targets <= that level are implicitly enabled. The lowest numbered
// target level is always enabled for any platform.
// This is mostly just to be able to experiment in a controlled manner with levels
// _higher_ than what's enabled by default.

#ifdef __x86_64__
constexpr uint32_t AVX3_DL = 3;
constexpr uint32_t AVX3 = 2;
constexpr uint32_t AVX2 = 1;
constexpr uint32_t X64_GENERIC = 0;
#else
constexpr uint32_t SVE2 = 3;
constexpr uint32_t SVE = 2;
constexpr uint32_t NEON_FP16_DOTPROD = 1;
constexpr uint32_t NEON = 0;
#endif

#ifdef __x86_64__
constexpr uint32_t DEFAULT_LEVEL = AVX3;
#else
constexpr uint32_t DEFAULT_LEVEL = NEON_FP16_DOTPROD;
#endif

[[nodiscard]] const char* level_u32_to_str(uint32_t level) noexcept {
    switch (level) {
#ifdef __x86_64__
    case AVX3_DL:
        return "AVX3_DL";
    case AVX3:
        return "AVX3";
    case AVX2:
        return "AVX2";
    default:
        return "X64_GENERIC";
#else
    case SVE2:
        return "SVE2";
    case SVE:
        return "SVE";
    case NEON_FP16_DOTPROD:
        return "NEON_FP16_DOTPROD";
    case NEON:
        return "NEON";
    default:
        return "NEON";
#endif
    }
}

[[nodiscard]] uint32_t level_str_to_u32(const std::string& str) noexcept {
#ifdef __x86_64__
    if (str == "AVX3_DL") {
        return AVX3_DL;
    } else if (str == "AVX3") {
        return AVX3;
    } else if (str == "AVX2") {
        return AVX2;
    } else if (str == "X64_GENERIC") {
        return X64_GENERIC;
    }
#else
    if (str == "SVE2") {
        return SVE2;
    } else if (str == "SVE") {
        return SVE;
    } else if (str == "NEON_FP16_DOTPROD") {
        return NEON_FP16_DOTPROD;
    } else if (str == "NEON") {
        return NEON;
    }
#endif
    LOG(info, "Unknown vectorization target level for " VESPA_HWACCEL_ARCH_NAME ": '%s'. Using %s.", str.c_str(),
        level_u32_to_str(DEFAULT_LEVEL));
    return DEFAULT_LEVEL;
}

[[nodiscard]] uint32_t max_supported_level() noexcept {
#ifdef __x86_64__
    __builtin_cpu_init();
    if (supports_avx3_dl_target()) {
        return AVX3_DL;
    }
    if (supports_avx3_target()) {
        return AVX3;
    }
    if (supports_avx2_target()) {
        return AVX2;
    }
    return X64_GENERIC;
#else // aarch64
    if (supports_sve2()) {
        return SVE2;
    }
    if (supports_sve()) {
        return SVE;
    }
    if (supports_neon_aes_fp16_and_dotprod()) {
        return NEON_FP16_DOTPROD;
    }
    return NEON; // A NEON baseline is always supported on aarch64
#endif
}

} // namespace target

class EnabledTargetLevel {
    const uint32_t _max_native_level;
    const bool     _with_highway;

public:
    constexpr EnabledTargetLevel(uint32_t max_native_level, bool with_highway) noexcept
        : _max_native_level(max_native_level), _with_highway(with_highway) {}
    [[maybe_unused]] [[nodiscard]] bool is_enabled(uint32_t level) const noexcept {
        return level <= _max_native_level;
    }
    [[nodiscard]] bool                      with_highway() const noexcept { return _with_highway; }
    [[nodiscard]] static EnabledTargetLevel create_from_env_var();
};

constexpr bool should_use_highway_by_default() noexcept { return true; }

EnabledTargetLevel EnabledTargetLevel::create_from_env_var() {
    const uint32_t supported_level = target::max_supported_level();
    const uint32_t default_enabled_level = std::min(target::DEFAULT_LEVEL, supported_level);
    // This is a variable for internal testing only. If you're _not_ using this for internal
    // Vespa testing, I will break into your kitchen and make a mess out of your pots and pans.
    const char* maybe_var = getenv("VESPA_INTERNAL_VECTORIZATION_TARGET_LEVEL");
    if (maybe_var == nullptr) {
        return {default_enabled_level, should_use_highway_by_default()};
    }
    std::string target_var(maybe_var);
    if (target_var == "HIGHWAY") {
        return {default_enabled_level, true};
    }
    // There is an explicit target override, but it's specifying an auto-vectorized target
    const uint32_t wanted_level = target::level_str_to_u32(target_var);
    if (wanted_level > supported_level) {
        LOG(info, "Requested vectorization target level is %s, but platform only supports %s.",
            target::level_u32_to_str(wanted_level), target::level_u32_to_str(supported_level));
    }
    const uint32_t enabled_level = std::min(wanted_level, supported_level);
    LOG(debug, "Using vectorization target level %s", target::level_u32_to_str(enabled_level));
    return {enabled_level, false};
}

[[nodiscard]] EnabledTargetLevel enabled_target_level() {
    static auto target_level = EnabledTargetLevel::create_from_env_var();
    return target_level;
}

template <typename T> std::vector<T> create_and_fill(size_t sz) {
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = rand() % 100;
    }
    return v;
}

template <typename T, typename SumT = T> void verify_dot_product() {
    constexpr size_t test_length = 255;
    srand(1);
    std::vector<T> a = create_and_fill<T>(test_length);
    std::vector<T> b = create_and_fill<T>(test_length);
    for (size_t j(0); j < 0x20; j++) {
        SumT sum(0);
        for (size_t i(j); i < test_length; i++) {
            sum += a[i] * b[i];
        }
        SumT hwComputedSum(dot_product(&a[j], &b[j], test_length - j));
        if (sum != hwComputedSum) {
            fprintf(stderr, "Accelerator is not computing dotproduct correctly.\n");
            LOG_ABORT("should not be reached");
        }
    }
}

template <typename T, typename SumT = T> void verify_euclidean_distance() {
    constexpr size_t test_length = 255;
    srand(1);
    std::vector<T> a = create_and_fill<T>(test_length);
    std::vector<T> b = create_and_fill<T>(test_length);
    for (size_t j(0); j < 0x20; j++) {
        SumT sum(0);
        for (size_t i(j); i < test_length; i++) {
            sum += (a[i] - b[i]) * (a[i] - b[i]);
        }
        SumT hwComputedSum(squared_euclidean_distance(&a[j], &b[j], test_length - j));
        if (sum != hwComputedSum) {
            fprintf(stderr, "Accelerator is not computing euclidean distance correctly.\n");
            LOG_ABORT("should not be reached");
        }
    }
}

void verify_population_count() {
    const uint64_t words[7] = {
        0x123456789abcdef0L,  // 32
        0x0000000000000000L,  // 0
        0x8000000000000000L,  // 1
        0xdeadbeefbeefdeadUL, // 48
        0x5555555555555555L,  // 32
        0x00000000000000001,  // 1
        0xffffffffffffffff};  // 64
    constexpr size_t expected = 32 + 0 + 1 + 48 + 32 + 1 + 64;
    size_t           hwComputedPopulationCount = population_count(words, VESPA_NELEMS(words));
    if (hwComputedPopulationCount != expected) {
        fprintf(stderr, "Accelerator is not computing populationCount correctly. Expected %zu, computed %zu\n",
                expected, hwComputedPopulationCount);
        LOG_ABORT("should not be reached");
    }
}

void fill(std::vector<uint64_t>& v, size_t n) {
    v.reserve(n);
    for (size_t i(0); i < n; i++) {
        v.emplace_back(random());
    }
}

void simple_and_with(std::vector<uint64_t>& dest, const std::vector<uint64_t>& src) {
    for (size_t i(0); i < dest.size(); i++) {
        dest[i] &= src[i];
    }
}

void simple_or_with(std::vector<uint64_t>& dest, const std::vector<uint64_t>& src) {
    for (size_t i(0); i < dest.size(); i++) {
        dest[i] |= src[i];
    }
}

std::vector<uint64_t> simple_invert(const std::vector<uint64_t>& src) {
    std::vector<uint64_t> inverted;
    inverted.reserve(src.size());
    for (uint64_t i : src) {
        inverted.push_back(~i);
    }
    return inverted;
}

std::vector<uint64_t> optionally_invert(bool invert, std::vector<uint64_t> v) {
    return invert ? simple_invert(v) : std::move(v);
}

bool should_invert(bool invertSome) { return invertSome ? (random() & 1) : false; }

void verify_or_128(
    const std::vector<std::vector<uint64_t>>& vectors, size_t offset, size_t num_vectors, bool invertSome) {
    std::vector<std::pair<const void*, bool>> vRefs;
    for (size_t j(0); j < num_vectors; j++) {
        vRefs.emplace_back(&vectors[j][0], should_invert(invertSome));
    }

    std::vector<uint64_t> expected = optionally_invert(vRefs[0].second, vectors[0]);
    for (size_t j = 1; j < num_vectors; j++) {
        simple_or_with(expected, optionally_invert(vRefs[j].second, vectors[j]));
    }

    uint64_t dest[16] __attribute((aligned(64)));
    or_128(offset * sizeof(uint64_t), vRefs, dest);
    int diff = memcmp(&expected[offset], dest, sizeof(dest));
    if (diff != 0) {
        LOG_ABORT("Accelerator fails to compute correct 128 bytes OR");
    }
}

void verify_and_128(
    const std::vector<std::vector<uint64_t>>& vectors, size_t offset, size_t num_vectors, bool invertSome) {
    std::vector<std::pair<const void*, bool>> vRefs;
    for (size_t j(0); j < num_vectors; j++) {
        vRefs.emplace_back(&vectors[j][0], should_invert(invertSome));
    }
    std::vector<uint64_t> expected = optionally_invert(vRefs[0].second, vectors[0]);
    for (size_t j = 1; j < num_vectors; j++) {
        simple_and_with(expected, optionally_invert(vRefs[j].second, vectors[j]));
    }

    uint64_t dest[16] __attribute((aligned(64)));
    and_128(offset * sizeof(uint64_t), vRefs, dest);
    int diff = memcmp(&expected[offset], dest, sizeof(dest));
    if (diff != 0) {
        LOG_ABORT("Accelerator fails to compute correct 128 bytes AND");
    }
}

void verify_or_128() {
    std::vector<std::vector<uint64_t>> vectors(3);
    for (auto& v : vectors) {
        fill(v, 32);
    }
    for (size_t offset = 0; offset < 16; offset++) {
        for (size_t i = 1; i < vectors.size(); i++) {
            verify_or_128(vectors, offset, i, false);
            verify_or_128(vectors, offset, i, true);
        }
    }
}

void verify_and_128() {
    std::vector<std::vector<uint64_t>> vectors(3);
    for (auto& v : vectors) {
        fill(v, 32);
    }
    for (size_t offset = 0; offset < 16; offset++) {
        for (size_t i = 1; i < vectors.size(); i++) {
            verify_and_128(vectors, offset, i, false);
            verify_and_128(vectors, offset, i, true);
        }
    }
}

void verify_active_function_table() {
    verify_dot_product<float>();
    verify_dot_product<double>();
    verify_dot_product<int8_t, int64_t>();
    verify_dot_product<int32_t, int64_t>();
    verify_dot_product<int64_t>();
    verify_euclidean_distance<int8_t, int64_t>();
    verify_euclidean_distance<float>();
    verify_euclidean_distance<double>();
    verify_population_count();
    verify_and_128();
    verify_or_128();
}

// noexcept note: it is technically possible that something transitive here
// will throw, but then we _want_ the process to immediately terminate.
[[nodiscard]] FnTable build_optimal_fn_table() noexcept {
    std::vector<FnTable> fn_tables;
    // Both Highway and auto-vectorized target vectors are ordered so that the best targets
    // are at the front and the worst targets are at the back.
    // Since we prefer Highway over auto-vectorization, append the latter's targets at the end.
    const auto target_level = enabled_target_level();
    if (target_level.with_highway()) {
        for (const auto& hwy_target : Highway::create_supported_targets()) {
            fn_tables.emplace_back(hwy_target->fn_table());
        }
    }
    for (const auto& auto_vec_target : IAccelerated::create_supported_auto_vectorized_targets()) {
        fn_tables.emplace_back(auto_vec_target->fn_table());
    }
    return dispatch::build_composite_fn_table(fn_tables, true);
}

} // namespace

std::vector<std::unique_ptr<IAccelerated>> IAccelerated::create_supported_auto_vectorized_targets() {
    const auto                                 target_level = enabled_target_level();
    std::vector<std::unique_ptr<IAccelerated>> targets;
#ifdef __x86_64__
    if (target_level.is_enabled(target::AVX3_DL)) {
        targets.emplace_back(std::make_unique<Avx3DlAccelerator>());
    }
    if (target_level.is_enabled(target::AVX3)) {
        targets.emplace_back(std::make_unique<Avx3Accelerator>());
    }
    if (target_level.is_enabled(target::AVX2)) {
        targets.emplace_back(std::make_unique<Avx2Accelerator>());
    }
    targets.emplace_back(std::make_unique<X64GenericAccelerator>());
#else // aarch64
    if (target_level.is_enabled(target::SVE2)) {
        targets.emplace_back(std::make_unique<Sve2Accelerator>());
    }
    if (target_level.is_enabled(target::SVE)) {
        targets.emplace_back(std::make_unique<SveAccelerator>());
    }
    if (target_level.is_enabled(target::NEON_FP16_DOTPROD)) {
        targets.emplace_back(std::make_unique<NeonFp16DotprodAccelerator>());
    }
    targets.emplace_back(std::make_unique<NeonAccelerator>());
#endif
    return targets;
}

namespace dispatch {

#define VESPA_HWACCEL_PATCH_FN_TABLE_VISITOR(fn_type, fn_field, fn_id)                                          \
    if ((src_tbl.fn_field) != nullptr && (!exclude_suboptimal || !src_tbl.fn_is_tagged_as_suboptimal(fn_id))) { \
        composite_tbl.fn_field = src_tbl.fn_field;                                                              \
        composite_tbl.fn_target_infos[static_cast<size_t>(fn_id)] =                                             \
            src_tbl.fn_target_infos[static_cast<size_t>(fn_id)];                                                \
    }

FnTable build_composite_fn_table(std::span<const FnTable> fn_tables, bool exclude_suboptimal) noexcept {
    assert(!fn_tables.empty());
    FnTable composite_tbl;
    // Start at the back (worst) and move towards the front (best), patching in present
    // function pointers as we go (assuming not suboptimal & excluded). Painter's algorithm
    // for function pointers!
    for (const auto& src_tbl : std::ranges::reverse_view(fn_tables)) {
        VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_PATCH_FN_TABLE_VISITOR);
    }
    return composite_tbl;
}

FnTable build_composite_fn_table(
    const FnTable& fn_table, const FnTable& base_table, bool exclude_suboptimal) noexcept {
    std::vector<FnTable> fn_tables;
    fn_tables.emplace_back(fn_table);
    fn_tables.emplace_back(base_table);
    return build_composite_fn_table(fn_tables, exclude_suboptimal);
}

FnTable optimal_composite_fn_table() noexcept {
    static auto global_table = build_optimal_fn_table();
    return global_table;
}

namespace {

struct BuildFnTableAndPatchFunctionsAtStartup {
    BuildFnTableAndPatchFunctionsAtStartup();
};

BuildFnTableAndPatchFunctionsAtStartup::BuildFnTableAndPatchFunctionsAtStartup() {
    thread_unsafe_update_function_dispatch_pointers(optimal_composite_fn_table());
    // "Power on self-test" of active vectorization kernels
    verify_active_function_table();
}

BuildFnTableAndPatchFunctionsAtStartup build_fn_table_once;

#define VESPA_HWACCEL_DEBUG_LOG_FN_ENTRY(fn_type, fn_field, fn_id)                    \
    LOG(debug, "%s => %s", #fn_field, tbl.fn_target_info(fn_id).to_string().c_str());

void debug_log_fn_table_update(const FnTable& tbl) {
    LOG(debug, "Updating global vectorization function dispatch table:");
    VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_DEBUG_LOG_FN_ENTRY);
}

FnTable& mutable_active_fn_table() noexcept {
    static FnTable active_table;
    return active_table;
}

} // namespace

const FnTable& active_fn_table() noexcept {
    return mutable_active_fn_table(); // ... but as const
}

#define VESPA_HWACCEL_COPY_TABLE_TO_FN_PTR_VISITOR(fn_type, fn_field, fn_id) \
    VESPA_HWACCEL_DISPATCH_FN_PTR_NAME(fn_field) = fns.fn_field;

void thread_unsafe_update_function_dispatch_pointers(const FnTable& fns) {
    assert(fns.is_complete());
    if (LOG_WOULD_LOG(debug)) {
        debug_log_fn_table_update(fns);
    }
    // Thread safety note: we expect there to exist one singular thread during
    // invocation of this function, meaning that all subsequent loads of the
    // function pointers must happen-after these stores. Anything else would be
    // a terrible sin, and we can't have any of that!
    mutable_active_fn_table() = fns;
    VESPA_HWACCEL_VISIT_FN_TABLE(VESPA_HWACCEL_COPY_TABLE_TO_FN_PTR_VISITOR);
}

} // namespace dispatch

} // namespace vespalib::hwaccelerated
