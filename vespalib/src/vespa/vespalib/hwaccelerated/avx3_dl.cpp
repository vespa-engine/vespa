// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx3_dl.h"
#include "avxprivate.hpp"

namespace vespalib::hwaccelerated {

float
Avx3DlAccelerator::dotProduct(const float* af, const float* bf, size_t sz) const noexcept {
    return avx::dotProductSelectAlignment<float, 64>(af, bf, sz);
}

double
Avx3DlAccelerator::dotProduct(const double* af, const double* bf, size_t sz) const noexcept {
    return avx::dotProductSelectAlignment<double, 64>(af, bf, sz);
}

size_t
Avx3DlAccelerator::populationCount(const uint64_t* a, size_t sz) const noexcept {
    // Empirical measurements show that firing up the whole pile of massively compiler-unrolled
    // AVX512 VPOPCNT machinery for short vectors is actually determinental to performance.
    // We therefore specialize the code generation for short vectors.
    // This sequence of apparent identical function calls may look odd, but for each distinct
    // branch `helper::populationCount` is inline and the compiler knows the max loop trip count.
    // It can therefore specialize the implementations.
    // Empirically on GCC 14.2, each increasing trip count uses less and less POPCNT and more
    // and more VPOPCNT, culminating with the arbitrary trip count implementation.
    if (sz <= 8) {
        return helper::populationCount(a, sz);
    } else if (sz <= 16) {
        return helper::populationCount(a, sz);
    } else if (sz <= 32) {
        return helper::populationCount(a, sz);
    } else {
        return helper::populationCount(a, sz);
    }
}

double
Avx3DlAccelerator::squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept {
    return helper::squaredEuclideanDistance(a, b, sz);
}

double
Avx3DlAccelerator::squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept {
    return avx::euclideanDistanceSelectAlignment<float, 64>(a, b, sz);
}

double
Avx3DlAccelerator::squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept {
    return avx::euclideanDistanceSelectAlignment<double, 64>(a, b, sz);
}

void
Avx3DlAccelerator::and128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept {
    helper::andChunks<64, 2>(offset, src, dest);
}

void
Avx3DlAccelerator::or128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept {
    helper::orChunks<64, 2>(offset, src, dest);
}

void
Avx3DlAccelerator::convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) const noexcept {
    helper::convert_bfloat16_to_float(src, dest, sz);
}

int64_t
Avx3DlAccelerator::dotProduct(const int8_t* a, const int8_t* b, size_t sz) const noexcept
{
    return helper::multiplyAdd(a, b, sz);
}

}
