// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx2.h"
#include "avxprivate.hpp"

namespace vespalib::hwaccelrated {

float
Avx2Accelrator::dotProduct(const float * af, const float * bf, size_t sz) const
{
    return avx::dotProductSelectAlignment<float, 32>(af, bf, sz);
}

double
Avx2Accelrator::dotProduct(const double * af, const double * bf, size_t sz) const
{
    return avx::dotProductSelectAlignment<double, 32>(af, bf, sz);
}

size_t
Avx2Accelrator::populationCount(const uint64_t *a, size_t sz) const {
    return helper::populationCount(a, sz);
}

}
