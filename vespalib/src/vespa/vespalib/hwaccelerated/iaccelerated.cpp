// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "iaccelerated.h"
#ifdef __x86_64__
#    define VESPA_HWACCEL_ARCH_NAME "x86-64"
#    include "x64_generic.h"
#    include "avx2.h"
#    include "avx3.h"
#    include "avx3_dl.h"
#else // aarch64
#    define VESPA_HWACCEL_ARCH_NAME "aarch64"
#    include "neon.h"
#    include "neon_fp16_dotprod.h"
// There's no __builtin_cpu_supports() on aarch64, so we have to go via getauxval()
// HW capability bits, _iff_ it exists on the platform. Otherwise we just have to
// assume that the default baseline build settings are OK.
#    if defined(__has_include)
#        if __has_include(<asm/hwcap.h>)
#            define VESPA_HAS_AARCH64_HWCAP_H
#        endif
#    endif
#endif
#include <vespa/vespalib/util/memory.h>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

#ifdef VESPA_HAS_AARCH64_HWCAP_H
#    include <sys/auxv.h> // getauxval()
#    include <asm/hwcap.h> // AT_HWCAP / AT_HWCAP2 definitions
#endif

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.hwaccelerated");

namespace vespalib::hwaccelerated {

namespace {

#ifdef __x86_64__

[[nodiscard]] bool supports_avx2_target() noexcept {
    // TODO should this also check for BMI2, F16 and FMA?
    return __builtin_cpu_supports("avx2");
}

// AVX3 is ~Skylake with AVX512{F, VL, DQ, BW, CD}
[[nodiscard]] bool supports_avx3_target() noexcept {
    // TODO should this check for "x86-64-v4" instead? v4 corresponds to Skylake
    return (__builtin_cpu_supports("avx512f") &&
            __builtin_cpu_supports("avx512vl") &&
            __builtin_cpu_supports("avx512dq") &&
            __builtin_cpu_supports("avx512bw") &&
            __builtin_cpu_supports("avx512cd"));
}

// AVX3_DL corresponds to Icelake Server(-ish). See `avx3_dl.h` for list of required CPU
// features. We check as many of those features as possible here (everything except VAES).
// There's currently no "x86-64-vN" alias with an N high enough to cover this target,
// so we have to do things the hard way.
[[nodiscard]] bool supports_avx3_dl_target() noexcept {
    return (supports_avx3_target() &&
            __builtin_cpu_supports("avx512vnni") &&
            __builtin_cpu_supports("vpclmulqdq") &&
            __builtin_cpu_supports("avx512vbmi") &&
            __builtin_cpu_supports("avx512vbmi2") &&
            __builtin_cpu_supports("avx512vpopcntdq") &&
            __builtin_cpu_supports("avx512bitalg") &&
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
    const bool has_aes     = (hw & HWCAP_AES) != 0;
    const bool has_fp16    = (hw & HWCAP_ASIMDHP) != 0;
    const bool has_dotprod = (hw & HWCAP_ASIMDDP) != 0;
    return (has_aes && has_fp16 && has_dotprod);
#else
    return false;
#endif
#else
    return false;
#endif // VESPA_HAS_AARCH64_HWCAP_H
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
constexpr static uint32_t AVX3_DL           = 3;
constexpr static uint32_t AVX3              = 2;
constexpr static uint32_t AVX2              = 1;
constexpr static uint32_t X64_GENERIC       = 0;
#else
constexpr static uint32_t NEON_FP16_DOTPROD = 1;
constexpr static uint32_t NEON              = 0;
#endif

#ifdef __x86_64__
constexpr static uint32_t DEFAULT_LEVEL = AVX3;
#else
constexpr static uint32_t DEFAULT_LEVEL = NEON_FP16_DOTPROD;
#endif

[[nodiscard]] const char* level_u32_to_str(uint32_t level) noexcept {
    switch (level) {
#ifdef __x86_64__
    case AVX3_DL:           return "AVX3_DL";
    case AVX3:              return "AVX3";
    case AVX2:              return "AVX2";
    default:                return "X64_GENERIC";
#else
    case NEON_FP16_DOTPROD: return "NEON_DOTPROD";
    case NEON:              return "NEON";
    default:                return "NEON";
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
    if (str == "NEON_FP16_DOTPROD") {
        return NEON_FP16_DOTPROD;
    } else if (str == "NEON") {
        return NEON;
    }
#endif
    LOG(warning, "Unknown vectorization target level for " VESPA_HWACCEL_ARCH_NAME ": '%s'. Using %s.",
        str.c_str(), level_u32_to_str(DEFAULT_LEVEL));
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
    if (supports_neon_aes_fp16_and_dotprod()) {
        return NEON_FP16_DOTPROD;
    }
    return NEON; // A NEON baseline is always supported on aarch64
#endif
}

} // target

class EnabledTargetLevel {
    const uint32_t _max_level;
public:
    constexpr explicit EnabledTargetLevel(uint32_t max_level) noexcept : _max_level(max_level) {}
    [[maybe_unused]] [[nodiscard]] bool is_enabled(uint32_t level) const noexcept {
        return level <= _max_level;
    }
    [[nodiscard]] static EnabledTargetLevel create_from_env_var();
};

EnabledTargetLevel EnabledTargetLevel::create_from_env_var() {
    // This is a variable for internal testing only. If you're _not_ using this for internal
    // Vespa testing, I will break into your kitchen and make a mess out of your pots and pans.
    const char* maybe_var = getenv("VESPA_INTERNAL_VECTORIZATION_TARGET_LEVEL");
    const uint32_t wanted_level = (maybe_var != nullptr)
            ? target::level_str_to_u32(std::string(maybe_var))
            : target::DEFAULT_LEVEL;
    const uint32_t supported_level = target::max_supported_level();
    if (wanted_level > supported_level && (maybe_var != nullptr)) {
        LOG(info, "Requested vectorization target level is %s, but platform only supports %s.",
            target::level_u32_to_str(wanted_level), target::level_u32_to_str(supported_level));
    }
    const uint32_t enabled_level = std::min(wanted_level, supported_level);
    LOG(debug, "Using vectorization target level %s", target::level_u32_to_str(enabled_level));
    return EnabledTargetLevel(enabled_level);
}

IAccelerated::UP create_accelerator() {
    static auto target_level = EnabledTargetLevel::create_from_env_var();
#ifdef __x86_64__
    if (target_level.is_enabled(target::AVX3_DL)) {
        return std::make_unique<Avx3DlAccelerator>();
    }
    if (target_level.is_enabled(target::AVX3)) {
        return std::make_unique<Avx3Accelerator>();
    }
    if (target_level.is_enabled(target::AVX2)) {
        return std::make_unique<Avx2Accelerator>();
    }
#else // aarch64
    if (target_level.is_enabled(target::NEON_FP16_DOTPROD)) {
        return std::make_unique<NeonFp16DotprodAccelerator>();
    }
#endif
    return IAccelerated::create_platform_baseline_accelerator();
}

template<typename T>
std::vector<T> createAndFill(size_t sz) {
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = rand()%100;
    }
    return v;
}

template<typename T>
void
verifyDotproduct(const IAccelerated & accel)
{
    const size_t testLength(255);
    srand(1);
    std::vector<T> a = createAndFill<T>(testLength);
    std::vector<T> b = createAndFill<T>(testLength);
    for (size_t j(0); j < 0x20; j++) {
        T sum(0);
        for (size_t i(j); i < testLength; i++) {
            sum += a[i]*b[i];
        }
        T hwComputedSum(accel.dotProduct(&a[j], &b[j], testLength - j));
        if (sum != hwComputedSum) {
            fprintf(stderr, "Accelerator is not computing dotproduct correctly.\n");
            LOG_ABORT("should not be reached");
        }
    }
}

template<typename T>
void
verifyEuclideanDistance(const IAccelerated & accel) {
    const size_t testLength(255);
    srand(1);
    std::vector<T> a = createAndFill<T>(testLength);
    std::vector<T> b = createAndFill<T>(testLength);
    for (size_t j(0); j < 0x20; j++) {
        T sum(0);
        for (size_t i(j); i < testLength; i++) {
            sum += (a[i] - b[i]) * (a[i] - b[i]);
        }
        T hwComputedSum(accel.squaredEuclideanDistance(&a[j], &b[j], testLength - j));
        if (sum != hwComputedSum) {
            fprintf(stderr, "Accelerator is not computing euclidean distance correctly.\n");
            LOG_ABORT("should not be reached");
        }
    }
}

void
verifyPopulationCount(const IAccelerated & accel)
{
    const uint64_t words[7] = {0x123456789abcdef0L,  // 32
                               0x0000000000000000L,  // 0
                               0x8000000000000000L,  // 1
                               0xdeadbeefbeefdeadUL, // 48
                               0x5555555555555555L,  // 32
                               0x00000000000000001,  // 1
                               0xffffffffffffffff};  // 64
    constexpr size_t expected = 32 + 0 + 1 + 48 + 32 + 1 + 64;
    size_t hwComputedPopulationCount = accel.populationCount(words, VESPA_NELEMS(words));
    if (hwComputedPopulationCount != expected) {
        fprintf(stderr, "Accelerator is not computing populationCount correctly.Expected %zu, computed %zu\n", expected, hwComputedPopulationCount);
        LOG_ABORT("should not be reached");
    }
}

void
fill(std::vector<uint64_t> & v, size_t n) {
    v.reserve(n);
    for (size_t i(0); i < n; i++) {
        v.emplace_back(random());
    }
}

void
simpleAndWith(std::vector<uint64_t> & dest, const std::vector<uint64_t> & src) {
    for (size_t i(0); i < dest.size(); i++) {
        dest[i] &= src[i];
    }
}

void
simpleOrWith(std::vector<uint64_t> & dest, const std::vector<uint64_t> & src) {
    for (size_t i(0); i < dest.size(); i++) {
        dest[i] |= src[i];
    }
}

std::vector<uint64_t>
simpleInvert(const std::vector<uint64_t> & src) {
    std::vector<uint64_t> inverted;
    inverted.reserve(src.size());
    for (unsigned long i : src) {
        inverted.push_back(~i);
    }
    return inverted;
}

std::vector<uint64_t>
optionallyInvert(bool invert, std::vector<uint64_t> v) {
    return invert ? simpleInvert(v) : std::move(v);
}

bool shouldInvert(bool invertSome) {
    return invertSome ? (random() & 1) : false;
}

void
verifyOr64(const IAccelerated & accel, const std::vector<std::vector<uint64_t>> & vectors,
           size_t offset, size_t num_vectors, bool invertSome)
{
    std::vector<std::pair<const void *, bool>> vRefs;
    for (size_t j(0); j < num_vectors; j++) {
        vRefs.emplace_back(&vectors[j][0], shouldInvert(invertSome));
    }

    std::vector<uint64_t> expected = optionallyInvert(vRefs[0].second, vectors[0]);
    for (size_t j = 1; j < num_vectors; j++) {
        simpleOrWith(expected, optionallyInvert(vRefs[j].second, vectors[j]));
    }

    uint64_t dest[16] __attribute((aligned(64)));
    accel.or128(offset * sizeof(uint64_t), vRefs, dest);
    int diff = memcmp(&expected[offset], dest, sizeof(dest));
    if (diff != 0) {
        LOG_ABORT("Accelerator fails to compute correct 128 bytes OR");
    }
}

void
verifyAnd64(const IAccelerated & accel, const std::vector<std::vector<uint64_t>> & vectors,
           size_t offset, size_t num_vectors, bool invertSome)
{
    std::vector<std::pair<const void *, bool>> vRefs;
    for (size_t j(0); j < num_vectors; j++) {
        vRefs.emplace_back(&vectors[j][0], shouldInvert(invertSome));
    }
    std::vector<uint64_t> expected = optionallyInvert(vRefs[0].second, vectors[0]);
    for (size_t j = 1; j < num_vectors; j++) {
        simpleAndWith(expected, optionallyInvert(vRefs[j].second, vectors[j]));
    }

    uint64_t dest[16] __attribute((aligned(64)));
    accel.and128(offset * sizeof(uint64_t), vRefs, dest);
    int diff = memcmp(&expected[offset], dest, sizeof(dest));
    if (diff != 0) {
        LOG_ABORT("Accelerator fails to compute correct 128 bytes AND");
    }
}

void
verifyOr64(const IAccelerated & accel) {
    std::vector<std::vector<uint64_t>> vectors(3) ;
    for (auto & v : vectors) {
        fill(v, 32);
    }
    for (size_t offset = 0; offset < 16; offset++) {
        for (size_t i = 1; i < vectors.size(); i++) {
            verifyOr64(accel, vectors, offset, i, false);
            verifyOr64(accel, vectors, offset, i, true);
        }
    }
}

void
verifyAnd64(const IAccelerated & accel) {
    std::vector<std::vector<uint64_t>> vectors(3);
    for (auto & v : vectors) {
        fill(v, 32);
    }
    for (size_t offset = 0; offset < 16; offset++) {
        for (size_t i = 1; i < vectors.size(); i++) {
            verifyAnd64(accel, vectors, offset, i, false);
            verifyAnd64(accel, vectors, offset, i, true);
        }
    }
}

class RuntimeVerificator
{
public:
    RuntimeVerificator();
private:
    static void verify(const IAccelerated & accelerated) {
        verifyDotproduct<float>(accelerated);
        verifyDotproduct<double>(accelerated);
        verifyDotproduct<int8_t>(accelerated);
        verifyDotproduct<int32_t>(accelerated);
        verifyDotproduct<int64_t>(accelerated);
        verifyEuclideanDistance<int8_t>(accelerated);
        verifyEuclideanDistance<float>(accelerated);
        verifyEuclideanDistance<double>(accelerated);
        verifyPopulationCount(accelerated);
        verifyAnd64(accelerated);
        verifyOr64(accelerated);
    }
};

RuntimeVerificator::RuntimeVerificator()
{
    verify(*IAccelerated::create_platform_baseline_accelerator());
    verify(*create_accelerator());
}

} // anon ns

IAccelerated::UP IAccelerated::create_platform_baseline_accelerator() {
    // Important: must never recurse into create_accelerator(), as it defers to this function as a fallback.
#ifdef __x86_64__
    return std::make_unique<X64GenericAccelerator>();
#else
    return std::make_unique<NeonAccelerator>();
#endif
}

const IAccelerated &
IAccelerated::getAccelerator()
{
    static RuntimeVerificator verifyAccelerator_once;
    static IAccelerated::UP accelerator = create_accelerator();
    return *accelerator;
}

} // vespalib::hwaccelerated
