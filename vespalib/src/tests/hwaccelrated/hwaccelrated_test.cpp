// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <vespa/vespalib/hwaccelrated/generic.h>

using namespace vespalib;

template<typename T>
std::vector<T> createAndFill(size_t sz) {
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = i;
    }
    return v;
}

template<typename T>
void verifyEuclidianDistance(const hwaccelrated::IAccelrated & accel) {
    const size_t testLength(255);
    std::vector<T> a = createAndFill<T>(testLength);
    std::vector<T> b = createAndFill<T>(testLength);
    for (size_t j(0); j < 0x20; j++) {
        T sum(0);
        for (size_t i(j); i < testLength; i++) {
            sum += (a[i] - b[i]) * (a[i] - b[i]);
        }
        T hwComputedSum(accel.squaredEuclidianDistance(&a[j], &b[j], testLength - j));
        EXPECT_EQUAL (sum, hwComputedSum);
    }
}

TEST("test euclidian distance") {
    hwaccelrated::GenericAccelrator genericAccelrator;
    verifyEuclidianDistance<float>(genericAccelrator);
    verifyEuclidianDistance<double >(genericAccelrator);
}

TEST_MAIN() { TEST_RUN_ALL(); }
