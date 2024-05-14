// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx512.h"
#include "avxprivate.hpp"

namespace vespalib:: hwaccelrated {

float
Avx512Accelrator::dotProduct(const float * af, const float * bf, size_t sz) const noexcept {
    return avx::dotProductSelectAlignment<float, 64>(af, bf, sz);
}

double
Avx512Accelrator::dotProduct(const double * af, const double * bf, size_t sz) const noexcept {
    return avx::dotProductSelectAlignment<double, 64>(af, bf, sz);
}

size_t
Avx512Accelrator::populationCount(const uint64_t *a, size_t sz) const noexcept {
    return helper::populationCount(a, sz);
}

double
Avx512Accelrator::squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const noexcept {
    return helper::squaredEuclideanDistance(a, b, sz);
}

double
Avx512Accelrator::squaredEuclideanDistance(const float * a, const float * b, size_t sz) const noexcept {
    return avx::euclideanDistanceSelectAlignment<float, 64>(a, b, sz);
}

double
Avx512Accelrator::squaredEuclideanDistance(const double * a, const double * b, size_t sz) const noexcept {
    return avx::euclideanDistanceSelectAlignment<double, 64>(a, b, sz);
}

void
Avx512Accelrator::and128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept {
    helper::andChunks<64, 2>(offset, src, dest);
}

void
Avx512Accelrator::or128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept {
    helper::orChunks<64, 2>(offset, src, dest);
}

void
Avx512Accelrator::convert_bfloat16_to_float(const uint16_t * src, float * dest, size_t sz) const noexcept {
    helper::convert_bfloat16_to_float(src, dest, sz);
}

}
