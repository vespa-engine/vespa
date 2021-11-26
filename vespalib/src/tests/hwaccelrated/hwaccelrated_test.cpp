// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/hwaccelrated/generic.h>
#include <vespa/log/log.h>
LOG_SETUP("hwaccelrated_test");

using namespace vespalib;

template<typename T>
std::vector<T> createAndFill(size_t sz) {
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = rand()%500;
    }
    return v;
}

template<typename T, typename P>
void verifyEuclideanDistance(const hwaccelrated::IAccelrated & accel, size_t testLength, double approxFactor) {
    srand(1);
    std::vector<T> a = createAndFill<T>(testLength);
    std::vector<T> b = createAndFill<T>(testLength);
    for (size_t j(0); j < 0x20; j++) {
        P sum(0);
        for (size_t i(j); i < testLength; i++) {
            P d = P(a[i]) - P(b[i]);
            sum += d * d;
        }
        P hwComputedSum(accel.squaredEuclideanDistance(&a[j], &b[j], testLength - j));
        EXPECT_APPROX(sum, hwComputedSum, sum*approxFactor);
    }
}

void
verifyEuclideanDistance(const hwaccelrated::IAccelrated & accelrator, size_t testLength) {
    verifyEuclideanDistance<int8_t, double>(accelrator, testLength, 0.0);
    verifyEuclideanDistance<float, double>(accelrator, testLength, 0.0001); // Small deviation requiring EXPECT_APPROX
    verifyEuclideanDistance<double, double>(accelrator, testLength, 0.0);
}

TEST("test euclidean distance") {
    hwaccelrated::GenericAccelrator genericAccelrator;
    constexpr size_t TEST_LENGTH = 140000; // must be longer than 64k
    TEST_DO(verifyEuclideanDistance(hwaccelrated::GenericAccelrator(), TEST_LENGTH));
    TEST_DO(verifyEuclideanDistance(hwaccelrated::IAccelrated::getAccelerator(), TEST_LENGTH));
}

TEST_MAIN() { TEST_RUN_ALL(); }
