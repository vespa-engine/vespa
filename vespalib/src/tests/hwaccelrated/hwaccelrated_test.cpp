// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

void
verifyAnd64(const hwaccelrated::IAccelrated & accelrator, std::vector<std::pair<const void *, bool>> v,
            const char * expected, char * dest)
{
    accelrator.and64(0, v, dest);
    EXPECT_EQUAL(0, memcmp(expected, dest, 64));
}

void
verifyAnd64(const hwaccelrated::IAccelrated & accelrator, std::vector<std::pair<const void *, bool>> v, const char * expected)
{
    char c[64];
    memset(c, 0, sizeof(c));
    verifyAnd64(accelrator, v, expected, c);
    memset(c, 0xff, sizeof(c));
    verifyAnd64(accelrator, v, expected, c);
}

TEST("test 64 byte and with multiple vectors") {
    char a[64];
    char b[64];
    memset(a, 0x55, sizeof(a));
    memset(b, 0xff, sizeof(b));
    std::vector<std::pair<const void *, bool>> v;
    v.emplace_back(a, false);
    v.emplace_back(b, false);

    verifyAnd64(hwaccelrated::GenericAccelrator(), v, a);
    verifyAnd64(hwaccelrated::IAccelrated::getAccelerator(), v, a);
    std::reverse(v.begin(), v.end());
    verifyAnd64(hwaccelrated::GenericAccelrator(), v, a);
    verifyAnd64(hwaccelrated::IAccelrated::getAccelerator(), v, a);
}

TEST_MAIN() { TEST_RUN_ALL(); }
