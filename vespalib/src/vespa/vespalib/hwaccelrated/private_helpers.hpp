// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/optimized.h>

namespace vespalib::hwaccelrated::helper {
namespace {

inline size_t
populationCount(const uint64_t *a, size_t sz) {
    size_t count(0);
    size_t i(0);
    for (; (i + 3) < sz; i += 4) {
        count += Optimized::popCount(a[i + 0]) +
                 Optimized::popCount(a[i + 1]) +
                 Optimized::popCount(a[i + 2]) +
                 Optimized::popCount(a[i + 3]);
    }
    for (; i < sz; i++) {
        count += Optimized::popCount(a[i]);
    }
    return count;
}

template <typename T, size_t UNROLL>
double
euclidianDistanceT(const T * a, const T * b, size_t sz)
{
    T partial[UNROLL];
    for (size_t i(0); i < UNROLL; i++) {
        partial[i] = 0;
    }
    size_t i(0);
    for (; i + UNROLL <= sz; i += UNROLL) {
        for (size_t j(0); j < UNROLL; j++) {
            partial[j] += (a[i+j] - b[i+j]) * (a[i+j] - b[i+j]);
        }
    }
    for (;i < sz; i++) {
        partial[i%UNROLL] += (a[i] - b[i]) * (a[i] - b[i]);
    }
    double sum(0);
    for (size_t j(0); j < UNROLL; j++) {
        sum += partial[j];
    }
    return sum;
}

}
}
