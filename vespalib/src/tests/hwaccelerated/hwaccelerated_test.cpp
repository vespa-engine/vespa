// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <limits>
#include <random>

#include <vespa/log/log.h>
LOG_SETUP("hwaccelerated_test");

using namespace vespalib;

// TODO reconcile run-time startup verification in `iaccelerated.cpp` with what's in here!
//  Ideally we want to run our tests on hardware that has enough bells and whistles in terms
//  of supported targets that we don't have to re-run the same vectorization checks literally
//  _every single time_ we launch a C++ binary that transitively loads `libvespalib.so`...!

template <typename T, std::uniform_random_bit_generator Rng>
std::vector<T> createAndFill(Rng& rng, size_t sz) {
    // All supported types have a well-defined range of at least [-128, 127].
    std::uniform_int_distribution<> dist(-128, 127);
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = dist(rng);
    }
    return v;
}

template <typename T>
std::pair<std::vector<T>, std::vector<T>>
create_and_fill_lhs_rhs(size_t sz) {
    // TODO add Xoroshiro PRNG to vespalib. Mersenne Twister is too big and unwieldy for what it provides.
    std::minstd_rand prng;
    prng.seed(1234567);
    std::vector<T> a = createAndFill<T>(prng, sz);
    std::vector<T> b = createAndFill<T>(prng, sz);
    return {std::move(a), std::move(b)};
}

template <typename T>
void verify_euclidean_distance(std::span<const hwaccelerated::IAccelerated*> accels, size_t test_length, double approx_factor) {
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
void verify_dot_product(std::span<const hwaccelerated::IAccelerated*> accels, size_t test_length, double approx_factor) {
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

const hwaccelerated::IAccelerated* baseline_accelerator() {
    static auto baseline = hwaccelerated::IAccelerated::create_platform_baseline_accelerator();
    return baseline.get();
}

std::vector<const hwaccelerated::IAccelerated*> all_accelerators_to_test() {
    std::vector<const hwaccelerated::IAccelerated*> accels;
    accels.emplace_back(baseline_accelerator());
    accels.emplace_back(&hwaccelerated::IAccelerated::getAccelerator());
    const auto hwy_targets = hwaccelerated::Highway::supported_targets();
    for (const auto* t : hwy_targets) {
        accels.emplace_back(t);
    }
    return accels;
}

void verify_euclidean_distance(std::span<const hwaccelerated::IAccelerated*> accelerators, size_t testLength) {
    verify_euclidean_distance<int8_t>(accelerators, testLength, 0.0);
    verify_euclidean_distance<float>(accelerators, testLength, 0.0001); // Small deviation requiring EXPECT_APPROX
    verify_euclidean_distance<BFloat16>(accelerators, testLength, 0.001f); // Reduced BF16 precision requires more slack
    verify_euclidean_distance<double>(accelerators, testLength, 0.0);
}

TEST(HwAcceleratedTest, euclidean_distance_impls_match_source_of_truth) {
    auto accelerators = all_accelerators_to_test();
    // verify_euclidean_distance checks all sub ranges in [0, 32), so test lengths must be at least this long
    for (size_t test_length : {32, 64, 256, 1024, 140000}) {
        GTEST_DO(verify_euclidean_distance(accelerators, test_length));
    }
}

void verify_dot_product(std::span<const hwaccelerated::IAccelerated*> accelerators, size_t testLength) {
    verify_dot_product<int8_t>(accelerators, testLength, 0.0);
    verify_dot_product<int16_t>(accelerators, testLength, 0.0);
    verify_dot_product<int32_t>(accelerators, testLength, 0.0);
    verify_dot_product<int64_t>(accelerators, testLength, 0.0);
    verify_dot_product<float>(accelerators, testLength, 0.0001);
    verify_dot_product<BFloat16>(accelerators, testLength, 0.001f);
    verify_dot_product<double>(accelerators, testLength, 0.0);
}

TEST(HwAcceleratedTest, dot_product_impls_match_source_of_truth) {
    auto accelerators = all_accelerators_to_test();
    // verify_dot_product checks all sub ranges in [0, 32), so test lengths must be at least this long
    for (size_t test_length : {32, 64, 256, 1024, 140000}) {
        GTEST_DO(verify_dot_product(accelerators, test_length));
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
