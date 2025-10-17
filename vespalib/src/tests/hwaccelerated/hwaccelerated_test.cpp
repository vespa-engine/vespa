// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_utils.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <limits>
#include <random>

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
            LOG(spam, "verify_euclidean_distance(accel=%s, len=%zu, offset=%zu)", accel->target_name(), test_length, j);
            auto computed = static_cast<double>(accel->squaredEuclideanDistance(&a[j], &b[j], test_length - j));
            ASSERT_NEAR(sum, computed, sum*approx_factor) << accel->target_name();
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
            LOG(spam, "verify_dot_product(accel=%s, len=%zu, offset=%zu)", accel->target_name(), test_length, j);
            auto computed = static_cast<double>(accel->dotProduct(&a[j], &b[j], test_length - j));
            ASSERT_NEAR(sum, computed, std::fabs(sum*approx_factor)) << accel->target_name();
        }
    }
}

const IAccelerated* baseline_accelerator() {
    static auto baseline = IAccelerated::create_baseline_auto_vectorized_target();
    return baseline.get();
}

void fill_highway_accelerators(std::vector<const IAccelerated*>& accels) {
    static auto hwy_targets = Highway::create_supported_targets();
    for (const auto& t : hwy_targets) {
        accels.emplace_back(t.get());
    }
}

std::vector<const IAccelerated*> all_accelerators_to_test() {
    std::vector<const IAccelerated*> accels;
    accels.emplace_back(baseline_accelerator());
    accels.emplace_back(&IAccelerated::getAccelerator());
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
constexpr uint32_t max_chunk_i32_boundary = INT32_MAX / (INT8_MIN*INT8_MIN);

constexpr std::span<const size_t> test_lengths() noexcept {
    // verify_... checks all suffixes from offsets [0, 32), so test lengths must be at least this long.
    // Lengths relative to max_chunk_i32_boundary are for testing chunk overflow handling.
    static size_t lengths[] = {
        32u, 64u, 256u, 1024u, max_chunk_i32_boundary - 1, max_chunk_i32_boundary,
        max_chunk_i32_boundary + 1, max_chunk_i32_boundary + 256
    };
    return lengths;
}

struct HwAcceleratedTest : Test {
    static void SetUpTestSuite() {
        fprintf(stderr, "Testing accelerators:\n");
        for (const auto* accel : all_accelerators_to_test()) {
            fprintf(stderr, "%s\n", accel->friendly_name().c_str());
        }
    }
};

TEST_F(HwAcceleratedTest, euclidean_distance_impls_match_source_of_truth) {
    auto accelerators = all_accelerators_to_test();
    for (size_t test_length : test_lengths()) {
        GTEST_DO(verify_euclidean_distance(accelerators, test_length));
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
}

TEST_F(HwAcceleratedTest, dot_product_impls_match_source_of_truth) {
    auto accelerators = all_accelerators_to_test();
    for (size_t test_length : test_lengths()) {
        GTEST_DO(verify_dot_product(accelerators, test_length));
    }
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
    for (const auto* accel : accels) {
        ASSERT_EQ(accel->binary_hamming_distance(mem_a, mem_b, sz), dist) << accel->friendly_name();
    }
    while (dist * 2 < sz) {
        flip_one_bit(mem_a, mem_b, sz);
        ++dist;
        for (const auto* accel : accels) {
            ASSERT_EQ(accel->binary_hamming_distance(mem_a, mem_b, sz), dist) << accel->friendly_name();
        }
        flip_one_bit(mem_b, mem_a, sz);
        ++dist;
        for (const auto* accel : accels) {
            ASSERT_EQ(accel->binary_hamming_distance(mem_a, mem_b, sz), dist) << accel->friendly_name();
        }
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

} // vespalib::hwaccelerated

GTEST_MAIN_RUN_ALL_TESTS()
