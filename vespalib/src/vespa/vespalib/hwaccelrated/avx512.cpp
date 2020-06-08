// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
Avx512Accelrator::squaredEuclideanDistance(const float * a, const float * b, size_t sz) const {
    return avx::euclideanDistanceSelectAlignment<float, 64>(a, b, sz);
}

double
Avx512Accelrator::squaredEuclideanDistance(const double * a, const double * b, size_t sz) const {
    return avx::euclideanDistanceSelectAlignment<double, 64>(a, b, sz);
}

}
