// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include "scoped_fn_table_override.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/hwaccelerated/fn_table.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <vespa/vespalib/hwaccelerated/functions.h>
#include <vespa/vespalib/hwaccelerated/float8.h>
#include <vespa/vespalib/hwaccelerated/fp8_luts.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <limits>
#include <random>
#include <stdfloat>

#include <iomanip>

#include <vespa/log/log.h>
LOG_SETUP("hwaccelerated_test");

using namespace ::testing;

namespace vespalib::hwaccelerated {
// TODO reconcile run-time startup verification in `iaccelerated.cpp` with what's in here!
//  Ideally we want to run our tests on hardware that has enough bells and whistles in terms
//  of supported targets that we don't have to re-run the same vectorization checks literally
//  _every single time_ we launch a C++ binary that transitively loads `libvespalib.so`...!

template <typename T>
void verify_euclidean_distance(std::span<const IAccelerated*> accels, size_t test_length, double approx_factor) {
    auto [a, b] = create_and_fill_lhs_rhs<T>(test_length);
    for (size_t j = 0; j < 32; j++) {
        double sum = 0; // Assume a double has sufficient precision for all test inputs/outputs
        for (size_t i = j; i < test_length; i++) {
            auto d = a[i] - b[i];
            sum += d * d;
        }
        for (const auto* accel : accels) {
            LOG(spam, "verify_euclidean_distance(accel=%s, len=%zu, offset=%zu)", accel->target_info().to_string().c_str(), test_length, j);
            ScopedFnTableOverride fn_scope(accel->fn_table());
            double computed = squared_euclidean_distance(&a[j], &b[j], test_length - j);
            ASSERT_NEAR(sum, computed, sum*approx_factor) << accel->target_info().to_string();
        }
    }
}

template <typename T>
void verify_dot_product(std::span<const IAccelerated*> accels, size_t test_length, double approx_factor) {
    auto [a, b] = create_and_fill_lhs_rhs<T>(test_length);
    for (size_t j = 0; j < 32; j++) {
        double sum = 0; // Assume a double has sufficient precision for all test inputs/outputs
        for (size_t i = j; i < test_length; i++) {
            sum += a[i] * b[i];
        }
        for (const auto* accel : accels) {
            LOG(spam, "verify_dot_product(accel=%s, len=%zu, offset=%zu)", accel->target_info().to_string().c_str(), test_length, j);
            ScopedFnTableOverride fn_scope(accel->fn_table());
            auto computed = static_cast<double>(dot_product(&a[j], &b[j], test_length - j));
            ASSERT_NEAR(sum, computed, std::fabs(sum*approx_factor)) << accel->target_info().to_string();
        }
    }
}

// FP8 types have such low practical range (of which many are NaNs and/or Inf)
// that we use a custom test routine that prunes away all non-finite numbers.
template <typename T>
void verify_fp8_dot_product(std::span<const IAccelerated*> accels, size_t test_length, double approx_factor) {
    auto [a, b] = create_and_fill_lhs_rhs<uint8_t>(test_length, T::is_finite); // don't include NaN/Inf
    for (size_t j = 0; j < 32; j++) {
        float sum = 0;
        for (size_t i = j; i < test_length; i++) {
            sum += T(a[i]).to_float() * T(b[i]).to_float();
        }
        for (const auto* accel : accels) {
            LOG(spam, "verify_fp8_dot_product(accel=%s, len=%zu, offset=%zu)", accel->target_info().to_string().c_str(), test_length, j);
            ScopedFnTableOverride fn_scope(accel->fn_table());
            auto computed = dot_product(&a[j], &b[j], test_length - j, typename T::TagType{});
            ASSERT_NEAR(sum, computed, std::fabs(sum*approx_factor)) << accel->target_info().to_string();
        }
    }
}

void fill_auto_vectorized_accelerators(std::vector<const IAccelerated*>& accels) {
    static const auto auto_vec_targets = IAccelerated::create_supported_auto_vectorized_targets();
    for (const auto& t : auto_vec_targets) {
        accels.emplace_back(t.get()); // note: _static_ lifetime
    }
}

void fill_highway_accelerators(std::vector<const IAccelerated*>& accels) {
    static const auto hwy_targets = Highway::create_supported_targets();
    for (const auto& t : hwy_targets) {
        accels.emplace_back(t.get()); // note: _static_ lifetime
    }
}

std::vector<const IAccelerated*> all_accelerators_to_test() {
    std::vector<const IAccelerated*> accels;
    fill_auto_vectorized_accelerators(accels);
    fill_highway_accelerators(accels);
    return accels;
}

void verify_euclidean_distance(std::span<const IAccelerated*> accelerators, size_t testLength) {
    verify_euclidean_distance<int8_t>(accelerators, testLength, 0.0);
    verify_euclidean_distance<float>(accelerators, testLength, 0.0001); // Small deviation requiring EXPECT_APPROX
    verify_euclidean_distance<BFloat16>(accelerators, testLength, 0.001f); // Reduced BF16 precision requires more slack
    verify_euclidean_distance<double>(accelerators, testLength, 0.0);
}

// Max number of elements that can be covered in one computed_chunked_sum() call
// for our current chunked use cases (dot product + Euclidean distance).
constexpr uint32_t euclidean_max_chunk_i32_boundary = INT32_MAX / (-255 * -255);
constexpr uint32_t dot_max_chunk_i32_boundary = INT32_MAX / (-128 * -128);

constexpr std::span<const size_t> test_lengths() noexcept {
    // verify_... checks all suffixes from offsets [0, 32), so test lengths must be at least this long.
    // Lengths relative to max_chunk_i32_boundary limits are for testing chunk overflow handling.
    static size_t lengths[] = {
        32u, 64u, 256u, 1024u,
        euclidean_max_chunk_i32_boundary - 1, euclidean_max_chunk_i32_boundary,
        euclidean_max_chunk_i32_boundary + 1, euclidean_max_chunk_i32_boundary + 256,
        dot_max_chunk_i32_boundary - 1, dot_max_chunk_i32_boundary,
        dot_max_chunk_i32_boundary + 1, dot_max_chunk_i32_boundary + 256
    };
    return lengths;
}

struct HwAcceleratedTest : Test {
    static void SetUpTestSuite() {
        fprintf(stderr, "Testing accelerators:\n");
        for (const auto* accel : all_accelerators_to_test()) {
            fprintf(stderr, "%s\n", accel->target_info().to_string().c_str());
        }
    }
};

TEST_F(HwAcceleratedTest, euclidean_distance_impls_match_source_of_truth) {
    auto accelerators = all_accelerators_to_test();
    for (size_t test_length : test_lengths()) {
        ASSERT_NO_FATAL_FAILURE(verify_euclidean_distance(accelerators, test_length)) << "with length " << test_length;
    }
}

void verify_dot_product(std::span<const IAccelerated*> accelerators, size_t testLength) {
    verify_dot_product<int8_t>(accelerators, testLength, 0.0);
    verify_dot_product<int16_t>(accelerators, testLength, 0.0);
    verify_dot_product<int32_t>(accelerators, testLength, 0.0);
    verify_dot_product<int64_t>(accelerators, testLength, 0.0);
    verify_dot_product<float>(accelerators, testLength, 0.0001);
    verify_dot_product<BFloat16>(accelerators, testLength, 0.001f);
    verify_dot_product<double>(accelerators, testLength, 0.0);
    verify_fp8_dot_product<Float8E4M3FN>(accelerators, testLength, 0.0001);
    verify_fp8_dot_product<Float8E5M2>(accelerators, testLength, 0.0001);
}

TEST_F(HwAcceleratedTest, dot_product_impls_match_source_of_truth) {
    auto accelerators = all_accelerators_to_test();
    for (size_t test_length : test_lengths()) {
        ASSERT_NO_FATAL_FAILURE(verify_dot_product(accelerators, test_length)) << "with length " << test_length;
    }
}

template <std::integral T>
void verify_euclidean_distance_no_overflow(std::span<const IAccelerated*> accels, size_t test_length) {
    std::vector<T> lhs(test_length + 100);
    std::vector<T> rhs(test_length + 100);
    std::fill(lhs.begin(), lhs.end(), std::numeric_limits<T>::min());
    std::fill(rhs.begin(), rhs.end(), std::numeric_limits<T>::max());
    ASSERT_GE(test_length, 100);
    for (size_t i = test_length - 100; i < test_length + 100; i++) {
        int64_t sum = 0;
        for (size_t j = 0; j < i; j++) {
            auto d = lhs[j] - rhs[j];
            sum += d * d;
        }
        for (const auto* accel : accels) {
            LOG(spam, "verify_euclidean_distance_no_overflow(accel=%s, len=%zu)", accel->target_info().to_string().c_str(), i);
            ScopedFnTableOverride fn_scope(accel->fn_table());
            auto computed = static_cast<int64_t>(squared_euclidean_distance(lhs.data(), rhs.data(), i));
            ASSERT_EQ(sum, computed) << "overflow at length " << i << " for accel " << accel->target_info().to_string();
        }
    }
}

TEST_F(HwAcceleratedTest, chunked_i8_euclidean_distance_does_not_overflow) {
    auto accelerators = all_accelerators_to_test();
    verify_euclidean_distance_no_overflow<int8_t>(accelerators, euclidean_max_chunk_i32_boundary);
}

template <std::integral T>
void verify_dot_product_no_overflow(std::span<const IAccelerated*> accels, size_t test_length) {
    std::vector<T> lhs(test_length + 100);
    std::vector<T> rhs(test_length + 100);
    std::fill(lhs.begin(), lhs.end(), std::numeric_limits<T>::min());
    std::fill(rhs.begin(), rhs.end(), std::numeric_limits<T>::min());
    ASSERT_GE(test_length, 100);
    for (size_t i = test_length - 100; i < test_length + 100; i++) {
        int64_t sum = 0;
        for (size_t j = 0; j < i; j++) {
            sum += lhs[j] * rhs[j];
        }
        for (const auto* accel : accels) {
            LOG(spam, "verify_dot_product_no_overflow(accel=%s, len=%zu)", accel->target_info().to_string().c_str(), i);
            ScopedFnTableOverride fn_scope(accel->fn_table());
            int64_t computed = dot_product(lhs.data(), rhs.data(), i);
            ASSERT_EQ(sum, computed) << "overflow at length " << i << " for accel " << accel->target_info().to_string();
        }
    }
}

TEST_F(HwAcceleratedTest, chunked_i8_dot_product_does_not_overflow) {
    auto accelerators = all_accelerators_to_test();
    verify_dot_product_no_overflow<int8_t>(accelerators, dot_max_chunk_i32_boundary);
}

// TODO dedupe with hamming_test.cpp

class UnalignedPtr {
    void*  _aligned_ptr = nullptr;
    size_t _unalignment = 0;
public:
    constexpr UnalignedPtr(void* mem, size_t unalignment) noexcept
        : _aligned_ptr(mem),
          _unalignment(unalignment)
    {}
    // Make noncopyable/nonmovable for simplicity
    UnalignedPtr(const UnalignedPtr&) = delete;
    UnalignedPtr& operator=(const UnalignedPtr&) = delete;
    UnalignedPtr(UnalignedPtr&&) noexcept = delete;
    UnalignedPtr& operator=(UnalignedPtr&&) noexcept = delete;

    ~UnalignedPtr() {
        free(_aligned_ptr);
    }

    [[nodiscard]] void* as_unaligned_ptr() noexcept {
        return reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(_aligned_ptr) + _unalignment);
    }
};

[[nodiscard]] UnalignedPtr alloc_unaligned(size_t sz, size_t unalignment = 0) {
    constexpr size_t ALIGN = 8;
    void* mem;
    int r = posix_memalign(&mem, ALIGN, sz);
    assert(r == 0);
    return {mem, unalignment};
}

void flip_one_bit(void* memory, const void* other_memory, size_t sz) {
    auto* buf       = reinterpret_cast<uint8_t*>(memory);
    auto* other_buf = reinterpret_cast<const uint8_t*>(other_memory);
    while (true) {
        size_t byte_idx = random() % sz; // TODO non-deprecated PRNG
        size_t bit_idx = random() % 8; // TODO non-deprecated PRNG
        uint8_t cmp = other_buf[byte_idx];
        uint8_t old = buf[byte_idx];
        uint8_t bit = 1u << bit_idx;
        if ((old & bit) == (cmp & bit)) {
            uint8_t new_val = old ^ bit;
            assert(old != new_val);
            buf[byte_idx] = new_val;
            return;
        }
    }
}

void check_with_flipping(std::span<const IAccelerated*> accels, void* mem_a, void* mem_b, size_t sz) {
    memset(mem_a, 0, sz);
    memset(mem_b, 0, sz);
    size_t dist = 0;
    auto check_accelerators = [&] {
        for (const auto* accel : accels) {
            ScopedFnTableOverride fn_scope(accel->fn_table());
            ASSERT_EQ(binary_hamming_distance(mem_a, mem_b, sz), dist) << accel->target_info().to_string();
        }
    };
    ASSERT_NO_FATAL_FAILURE(check_accelerators());
    while (dist * 2 < sz) {
        flip_one_bit(mem_a, mem_b, sz);
        ++dist;
        ASSERT_NO_FATAL_FAILURE(check_accelerators());
        flip_one_bit(mem_b, mem_a, sz);
        ++dist;
        ASSERT_NO_FATAL_FAILURE(check_accelerators());
    }
}

void check_with_sizes(std::span<const IAccelerated*> accels, size_t lhs_unalign, size_t rhs_unalign) {
    auto mem_a = alloc_unaligned(512, lhs_unalign);
    auto mem_b = alloc_unaligned(512, rhs_unalign);
    for (size_t sz = 0; sz <= 257; ++sz) {
        ASSERT_NO_FATAL_FAILURE(check_with_flipping(accels, mem_a.as_unaligned_ptr(), mem_b.as_unaligned_ptr(), sz));
    }
}

TEST_F(HwAcceleratedTest, binary_hamming_distance_with_alignments) {
    auto accelerators = all_accelerators_to_test();
    std::vector<std::pair<size_t, size_t>> lhs_rhs_unalignments = {{0, 0}, {1, 0}, {0, 1}, {3, 0}, {0, 7}, {2, 6}};
    for (const auto& [lhs_unalign, rhs_unalign] : lhs_rhs_unalignments) {
        ASSERT_NO_FATAL_FAILURE(check_with_sizes(accelerators, lhs_unalign, rhs_unalign));
    }
}

using namespace dispatch;

void PrintTo(const TargetInfo& info, std::ostream* os) {
    *os << info.to_string();
}

int64_t my_dot_i8_a(const int8_t*, const int8_t*, size_t sz) noexcept {
    return sz;
}

int64_t my_dot_i8_b(const int8_t*, const int8_t*, size_t sz) noexcept {
    return sz;
}

size_t my_popcount(const uint64_t*, size_t sz) noexcept {
    return sz;
}

TargetInfo a_info("BoringImpl", "Dusty calculator", 128);
TargetInfo b_info("MyCoolImpl", "Liquid cooled 6502", 1024);

TEST(CompositeFnTableTest, functions_and_target_info_are_inherited_when_not_present) {
    FnTable a(a_info);
    a.dot_product_i8 = my_dot_i8_a;
    a.population_count = my_popcount;
    FnTable b(b_info);
    b.dot_product_i8 = my_dot_i8_b;
    b.tag_fns_as_suboptimal({FnTable::FnId::DOT_PRODUCT_I8}); // should not matter here

    // `c` is `b` built "on top" of `a`
    FnTable c = build_composite_fn_table(b, a, false); // _do not_ exclude suboptimal
    EXPECT_FALSE(c.is_complete());
    EXPECT_EQ(c.dot_product_i8, my_dot_i8_b);
    EXPECT_EQ(c.fn_target_info(FnTable::FnId::DOT_PRODUCT_I8), b_info);
    EXPECT_EQ(c.population_count, my_popcount);
    EXPECT_EQ(c.fn_target_info(FnTable::FnId::POPULATION_COUNT), a_info);
    EXPECT_FALSE(c.dot_product_bf16); // not set
}

TEST(CompositeFnTableTest, suboptimal_functions_are_not_used_when_exclusion_is_requested) {
    FnTable a(a_info);
    a.dot_product_i8 = my_dot_i8_a;
    FnTable b(b_info);
    b.dot_product_i8 = my_dot_i8_b;
    b.population_count = my_popcount;
    b.tag_fns_as_suboptimal({FnTable::FnId::DOT_PRODUCT_I8});

    FnTable c = build_composite_fn_table(b, a, true); // _exclude_ suboptimal
    EXPECT_FALSE(c.is_complete());
    // b's i8 dot product would be suboptimal and is not used. Use a's instead.
    EXPECT_EQ(c.dot_product_i8, my_dot_i8_a);
    EXPECT_EQ(c.fn_target_info(FnTable::FnId::DOT_PRODUCT_I8), a_info);
    EXPECT_EQ(c.population_count, my_popcount);
    EXPECT_EQ(c.fn_target_info(FnTable::FnId::POPULATION_COUNT), b_info);
}

TEST(CompositeFnTableTest, for_each_present_fn_invokes_callback_for_non_nullptr_fns) {
    FnTable tbl(b_info);
    tbl.dot_product_i8 = my_dot_i8_b;
    tbl.population_count = my_popcount;

    std::string seen_fns;
    tbl.for_each_present_fn([&](FnTable::FnId id) {
        seen_fns += FnTable::id_to_fn_name(id);
        seen_fns += " ";
    });
    EXPECT_EQ(seen_fns, "dot_product_i8 population_count ");
}

constexpr float fp32_inf = std::numeric_limits<float>::infinity();
constexpr float fp32_nan = std::numeric_limits<float>::quiet_NaN();

// 16 MSBs of raw bitwise representation of FP8E5M2 => F32 (16 LSBs are always zero).
// I.e. this is exactly their BFloat16 representation.
constexpr uint16_t fp8_e5m2_hi16_bits_lut[256] = {
    0x0000, 0x3780, 0x3800, 0x3840, 0x3880, 0x38a0, 0x38c0, 0x38e0,
    0x3900, 0x3920, 0x3940, 0x3960, 0x3980, 0x39a0, 0x39c0, 0x39e0,
    0x3a00, 0x3a20, 0x3a40, 0x3a60, 0x3a80, 0x3aa0, 0x3ac0, 0x3ae0,
    0x3b00, 0x3b20, 0x3b40, 0x3b60, 0x3b80, 0x3ba0, 0x3bc0, 0x3be0,
    0x3c00, 0x3c20, 0x3c40, 0x3c60, 0x3c80, 0x3ca0, 0x3cc0, 0x3ce0,
    0x3d00, 0x3d20, 0x3d40, 0x3d60, 0x3d80, 0x3da0, 0x3dc0, 0x3de0,
    0x3e00, 0x3e20, 0x3e40, 0x3e60, 0x3e80, 0x3ea0, 0x3ec0, 0x3ee0,
    0x3f00, 0x3f20, 0x3f40, 0x3f60, 0x3f80, 0x3fa0, 0x3fc0, 0x3fe0,
    0x4000, 0x4020, 0x4040, 0x4060, 0x4080, 0x40a0, 0x40c0, 0x40e0,
    0x4100, 0x4120, 0x4140, 0x4160, 0x4180, 0x41a0, 0x41c0, 0x41e0,
    0x4200, 0x4220, 0x4240, 0x4260, 0x4280, 0x42a0, 0x42c0, 0x42e0,
    0x4300, 0x4320, 0x4340, 0x4360, 0x4380, 0x43a0, 0x43c0, 0x43e0,
    0x4400, 0x4420, 0x4440, 0x4460, 0x4480, 0x44a0, 0x44c0, 0x44e0,
    0x4500, 0x4520, 0x4540, 0x4560, 0x4580, 0x45a0, 0x45c0, 0x45e0,
    0x4600, 0x4620, 0x4640, 0x4660, 0x4680, 0x46a0, 0x46c0, 0x46e0,
    0x4700, 0x4720, 0x4740, 0x4760, 0x7f80, 0x7fc0, 0x7fc0, 0x7fc0,
    0x8000, 0xb780, 0xb800, 0xb840, 0xb880, 0xb8a0, 0xb8c0, 0xb8e0,
    0xb900, 0xb920, 0xb940, 0xb960, 0xb980, 0xb9a0, 0xb9c0, 0xb9e0,
    0xba00, 0xba20, 0xba40, 0xba60, 0xba80, 0xbaa0, 0xbac0, 0xbae0,
    0xbb00, 0xbb20, 0xbb40, 0xbb60, 0xbb80, 0xbba0, 0xbbc0, 0xbbe0,
    0xbc00, 0xbc20, 0xbc40, 0xbc60, 0xbc80, 0xbca0, 0xbcc0, 0xbce0,
    0xbd00, 0xbd20, 0xbd40, 0xbd60, 0xbd80, 0xbda0, 0xbdc0, 0xbde0,
    0xbe00, 0xbe20, 0xbe40, 0xbe60, 0xbe80, 0xbea0, 0xbec0, 0xbee0,
    0xbf00, 0xbf20, 0xbf40, 0xbf60, 0xbf80, 0xbfa0, 0xbfc0, 0xbfe0,
    0xc000, 0xc020, 0xc040, 0xc060, 0xc080, 0xc0a0, 0xc0c0, 0xc0e0,
    0xc100, 0xc120, 0xc140, 0xc160, 0xc180, 0xc1a0, 0xc1c0, 0xc1e0,
    0xc200, 0xc220, 0xc240, 0xc260, 0xc280, 0xc2a0, 0xc2c0, 0xc2e0,
    0xc300, 0xc320, 0xc340, 0xc360, 0xc380, 0xc3a0, 0xc3c0, 0xc3e0,
    0xc400, 0xc420, 0xc440, 0xc460, 0xc480, 0xc4a0, 0xc4c0, 0xc4e0,
    0xc500, 0xc520, 0xc540, 0xc560, 0xc580, 0xc5a0, 0xc5c0, 0xc5e0,
    0xc600, 0xc620, 0xc640, 0xc660, 0xc680, 0xc6a0, 0xc6c0, 0xc6e0,
    0xc700, 0xc720, 0xc740, 0xc760, 0xff80, 0xffc0, 0xffc0, 0xffc0,
};

// Same for FP8E5M2_FNUZ (finite numbers only, unsigned zero)
constexpr uint16_t fp8_e5m2_fnuz_hi16_bits_lut[256] = {
    0x0000, 0x3a80, 0x3b00, 0x3b40, 0x3b80, 0x3ba0, 0x3bc0, 0x3be0,
    0x3c00, 0x3c10, 0x3c20, 0x3c30, 0x3c40, 0x3c50, 0x3c60, 0x3c70,
    0x3c80, 0x3c90, 0x3ca0, 0x3cb0, 0x3cc0, 0x3cd0, 0x3ce0, 0x3cf0,
    0x3d00, 0x3d10, 0x3d20, 0x3d30, 0x3d40, 0x3d50, 0x3d60, 0x3d70,
    0x3d80, 0x3d90, 0x3da0, 0x3db0, 0x3dc0, 0x3dd0, 0x3de0, 0x3df0,
    0x3e00, 0x3e10, 0x3e20, 0x3e30, 0x3e40, 0x3e50, 0x3e60, 0x3e70,
    0x3e80, 0x3e90, 0x3ea0, 0x3eb0, 0x3ec0, 0x3ed0, 0x3ee0, 0x3ef0,
    0x3f00, 0x3f10, 0x3f20, 0x3f30, 0x3f40, 0x3f50, 0x3f60, 0x3f70,
    0x3f80, 0x3f90, 0x3fa0, 0x3fb0, 0x3fc0, 0x3fd0, 0x3fe0, 0x3ff0,
    0x4000, 0x4010, 0x4020, 0x4030, 0x4040, 0x4050, 0x4060, 0x4070,
    0x4080, 0x4090, 0x40a0, 0x40b0, 0x40c0, 0x40d0, 0x40e0, 0x40f0,
    0x4100, 0x4110, 0x4120, 0x4130, 0x4140, 0x4150, 0x4160, 0x4170,
    0x4180, 0x4190, 0x41a0, 0x41b0, 0x41c0, 0x41d0, 0x41e0, 0x41f0,
    0x4200, 0x4210, 0x4220, 0x4230, 0x4240, 0x4250, 0x4260, 0x4270,
    0x4280, 0x4290, 0x42a0, 0x42b0, 0x42c0, 0x42d0, 0x42e0, 0x42f0,
    0x4300, 0x4310, 0x4320, 0x4330, 0x4340, 0x4350, 0x4360, 0x4370,
    0xffc0, 0xba80, 0xbb00, 0xbb40, 0xbb80, 0xbba0, 0xbbc0, 0xbbe0,
    0xbc00, 0xbc10, 0xbc20, 0xbc30, 0xbc40, 0xbc50, 0xbc60, 0xbc70,
    0xbc80, 0xbc90, 0xbca0, 0xbcb0, 0xbcc0, 0xbcd0, 0xbce0, 0xbcf0,
    0xbd00, 0xbd10, 0xbd20, 0xbd30, 0xbd40, 0xbd50, 0xbd60, 0xbd70,
    0xbd80, 0xbd90, 0xbda0, 0xbdb0, 0xbdc0, 0xbdd0, 0xbde0, 0xbdf0,
    0xbe00, 0xbe10, 0xbe20, 0xbe30, 0xbe40, 0xbe50, 0xbe60, 0xbe70,
    0xbe80, 0xbe90, 0xbea0, 0xbeb0, 0xbec0, 0xbed0, 0xbee0, 0xbef0,
    0xbf00, 0xbf10, 0xbf20, 0xbf30, 0xbf40, 0xbf50, 0xbf60, 0xbf70,
    0xbf80, 0xbf90, 0xbfa0, 0xbfb0, 0xbfc0, 0xbfd0, 0xbfe0, 0xbff0,
    0xc000, 0xc010, 0xc020, 0xc030, 0xc040, 0xc050, 0xc060, 0xc070,
    0xc080, 0xc090, 0xc0a0, 0xc0b0, 0xc0c0, 0xc0d0, 0xc0e0, 0xc0f0,
    0xc100, 0xc110, 0xc120, 0xc130, 0xc140, 0xc150, 0xc160, 0xc170,
    0xc180, 0xc190, 0xc1a0, 0xc1b0, 0xc1c0, 0xc1d0, 0xc1e0, 0xc1f0,
    0xc200, 0xc210, 0xc220, 0xc230, 0xc240, 0xc250, 0xc260, 0xc270,
    0xc280, 0xc290, 0xc2a0, 0xc2b0, 0xc2c0, 0xc2d0, 0xc2e0, 0xc2f0,
    0xc300, 0xc310, 0xc320, 0xc330, 0xc340, 0xc350, 0xc360, 0xc370,
};

namespace N_NEON_BF16 { // tee hee
void yolo_fp8e4m3_fp16(const uint8_t* a, uint16_t* out, size_t sz) noexcept;
void yolo_fp8e4m3_bf16(const uint8_t* a, uint16_t* out, size_t sz) noexcept;
}

TEST(Fp8Test, yolo_via_fp16) {
    constexpr size_t N = 16;
    uint8_t buf[N];
    uint16_t out[N];

    for (uint32_t i = 0; i < 256; i += N) {
        for (uint32_t j = 0; j < N; ++j) {
            buf[j] = i+j;
            out[j] = 0xffff;
        }
        N_NEON_BF16::yolo_fp8e4m3_fp16(buf, out, N);
        for (uint32_t j = 0; j < N; ++j) {
            const auto fp16_as_u16 = std::bit_cast<uint16_t>(static_cast<std::float16_t>(std::bit_cast<float>(fp8_e4m3fn_f32_bits_lut[buf[j]])));
            EXPECT_EQ(out[j], fp16_as_u16) << "for E4M3FN value " << j;
        }
    }
}

TEST(Fp8Test, yolo_via_bf16) {
    constexpr size_t N = 16;
    uint8_t buf[N];
    uint16_t out[N];

    for (uint32_t i = 0; i < 256; i += N) {
        for (uint32_t j = 0; j < N; ++j) {
            buf[j] = i+j;
            out[j] = 0xffff;
        }
        N_NEON_BF16::yolo_fp8e4m3_bf16(buf, out, N);
        for (uint32_t j = 0; j < N; ++j) {
            const uint16_t fp32_as_u16 = fp8_e4m3fn_f32_bits_lut[buf[j]] >> 16;
            EXPECT_EQ(out[j], fp32_as_u16) << "for E4M3FN value " << j;
        }
    }
}

TEST(Fp8TableGen, DISABLED_dump_fp16_bits) {
    for (uint32_t i = 0; i < 256; ++i) {
        auto fp16_as_u16 = std::bit_cast<uint16_t>(static_cast<std::float16_t>(std::bit_cast<float>(fp8_e4m3fn_f32_bits_lut[i])));
        const uint8_t fp8_e5_bits = (fp16_as_u16 & 0x7fff) >> 8;
        std::println(std::cout, "{:04b} {:04b} -> {:016b} -> {:016b} -> {:015b} -> {:08b}{}",
                     i >> 4, i & 0xf, (fp8_e4m3fn_f32_bits_lut[i] >> 16), fp16_as_u16,
                     fp16_as_u16 & 0x7fff, fp8_e5_bits, ((i & 0x7f) == 0x7f || (i & 0b01111000) == 0) ? " (SPECIAL)" : "");
    }
}

TEST(Fp8TableGen, DISABLED_dump_bf16_bits) {
    for (uint32_t i = 0; i < 256; ++i) {
        uint16_t fp32_as_u16 = fp8_e4m3fn_f32_bits_lut[i] >> 16;
        std::println(std::cout, "{:04b} {:04b} -> {:016b} -> {:08b} {:08b} {}",
                     i >> 4, i & 0xf, fp32_as_u16, uint16_t(fp32_as_u16 << 1) >> 8, uint16_t(fp32_as_u16 << 1) & 0xff,
                     ((i & 0x7f) == 0x7f || (i & 0b01111000) == 0) ? " (SPECIAL)" : "");
    }
}

} // vespalib::hwaccelerated

GTEST_MAIN_RUN_ALL_TESTS()
