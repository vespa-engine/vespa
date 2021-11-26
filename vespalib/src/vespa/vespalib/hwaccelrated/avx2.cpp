// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx2.h"
#include "avxprivate.hpp"

namespace vespalib::hwaccelrated {

size_t
Avx2Accelrator::populationCount(const uint64_t *a, size_t sz) const {
    return helper::populationCount(a, sz);
}

double
Avx2Accelrator::squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const {
    return helper::squaredEuclideanDistance(a, b, sz);
}

double
Avx2Accelrator::squaredEuclideanDistance(const float * a, const float * b, size_t sz) const {
    return avx::euclideanDistanceSelectAlignment<float, 32>(a, b, sz);
}

double
Avx2Accelrator::squaredEuclideanDistance(const double * a, const double * b, size_t sz) const {
    return avx::euclideanDistanceSelectAlignment<double, 32>(a, b, sz);
}

void
Avx2Accelrator::and64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const {
    helper::andChunks<32u, 2u>(offset, src, dest);
}

void
Avx2Accelrator::or64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const {
    helper::orChunks<32u, 2u>(offset, src, dest);
}

}
