// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "avx.h"
#include "avxprivate.hpp"

namespace vespalib {

namespace hwaccelrated {

float
AvxAccelrator::dotProduct(const float * af, const float * bf, size_t sz) const
{
    return avx::dotProductSelectAlignment<float, 32>(af, bf, sz);
}

double
AvxAccelrator::dotProduct(const double * af, const double * bf, size_t sz) const
{
    return avx::dotProductSelectAlignment<double, 32>(af, bf, sz);
}

}
}
