// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <vespa/vespalib/hwaccelerated/highway.h>
#include <limits>
#include <random>

#include <vespa/log/log.h>
LOG_SETUP("hwaccelerated_test");

using namespace vespalib;

template <typename T, std::uniform_random_bit_generator Rng>
std::vector<T> createAndFill(Rng& rng, size_t sz) {
    constexpr int max = std::min(static_cast<T>(500), std::numeric_limits<T>::max());
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = rng() % max;
    }
    return v;
}

template <typename T, typename P>
void verify_euclidean_distance(const hwaccelerated::IAccelerated& accel, size_t testLength, double approxFactor) {
    // TODO add Xoroshiro PRNG to vespalib. Mersenne Twister is too big and unwieldy for what it provides.
    std::minstd_rand prng;
    prng.seed(1234567);
    std::vector<T> a = createAndFill<T>(prng, testLength);
    std::vector<T> b = createAndFill<T>(prng, testLength);
    for (size_t j = 0; j < 32; j++) {
        P sum(0);
        for (size_t i = j; i < testLength; i++) {
            P d = P(a[i]) - P(b[i]);
            sum += d * d;
        }
        P hwComputedSum(accel.squaredEuclideanDistance(&a[j], &b[j], testLength - j));
        ASSERT_NEAR(sum, hwComputedSum, sum*approxFactor);
    }
}

void for_each_hwy_target(auto&& fn) {
    const auto hwy_targets = hwaccelerated::Highway::supported_targets();
    for (const auto* t : hwy_targets) {
        fn(*t);
    }
}

void
verify_euclidean_distance(const hwaccelerated::IAccelerated& accelerator, size_t testLength) {
    verify_euclidean_distance<int8_t, double>(accelerator, testLength, 0.0);
    verify_euclidean_distance<float, double>(accelerator, testLength, 0.0001); // Small deviation requiring EXPECT_APPROX
    verify_euclidean_distance<BFloat16, float>(accelerator, testLength, 0.01f); // Reduced BF16 precision requires more slack
    verify_euclidean_distance<double, double>(accelerator, testLength, 0.0);
}

TEST(HwAcceleratedTest, test_euclidean_distance) {
    // verify_euclidean_distance checks all sub ranges in [0, 32), so test lengths must be at least this long
    for (size_t test_length : {32, 64, 256, 1024, 140000}) {
        GTEST_DO(verify_euclidean_distance(*hwaccelerated::IAccelerated::create_platform_baseline_accelerator(), test_length));
        GTEST_DO(verify_euclidean_distance(hwaccelerated::IAccelerated::getAccelerator(), test_length));
        for_each_hwy_target([test_length](const hwaccelerated::IAccelerated& hwy_accel) {
            GTEST_DO(verify_euclidean_distance(hwy_accel, test_length));
        });
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
