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
    // When targeting VPOPCNTDQ the compiler auto-vectorization somewhat ironically
    // gets horribly confused when the code is explicitly written to do popcounts
    // in parallel across elements. Just doing a plain, boring loop lets the auto-
    // vectorizer understand the semantics of the loop much more easily.
    size_t count = 0;
    for (size_t i = 0; i < sz; ++i) {
        count += std::popcount(a[i]);
    }
    return count;
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
