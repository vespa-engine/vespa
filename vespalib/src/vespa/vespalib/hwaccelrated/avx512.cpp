// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx512.h"
#include "avxprivate.hpp"

namespace vespalib:: hwaccelrated {

float
Avx512Accelrator::dotProduct(const float * af, const float * bf, size_t sz) const
{
    return avx::dotProductSelectAlignment<float, 64>(af, bf, sz);
}

double
Avx512Accelrator::dotProduct(const double * af, const double * bf, size_t sz) const
{
    return avx::dotProductSelectAlignment<double, 64>(af, bf, sz);
}

size_t
Avx512Accelrator::populationCount(const uint64_t *a, size_t sz) const {
    return helper::populationCount(a, sz);
}

double
Avx512Accelrator::squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const {
    return helper::squaredEuclideanDistance(a, b, sz);
}

double
Avx512Accelrator::squaredEuclideanDistance(const float * a, const float * b, size_t sz) const {
    return avx::euclideanDistanceSelectAlignment<float, 64>(a, b, sz);
}

double
Avx512Accelrator::squaredEuclideanDistance(const double * a, const double * b, size_t sz) const {
    return avx::euclideanDistanceSelectAlignment<double, 64>(a, b, sz);
}

void
Avx512Accelrator::and64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const {
    helper::andChunks<64, 1>(offset, src, dest);
}

void
Avx512Accelrator::or64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const {
    helper::orChunks<64, 1>(offset, src, dest);
}

}
