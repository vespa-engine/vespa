// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/vespalib/hwaccelrated/avx.h>

namespace vespalib {

namespace hwaccelrated {

/**
 * Generic cpu agnostic implementation.
 */
class Avx2Accelrator : public AvxAccelrator
{
public:
    virtual float dotProduct(const float * a, const float * b, size_t sz) const;
    virtual double dotProduct(const double * a, const double * b, size_t sz) const;
private:
    template <typename T>
    VESPA_DLL_LOCAL static T dotProductSelectAlignment(const T * af, const T * bf, size_t sz);
    template <typename T, unsigned AlignA, unsigned AlignB>
    VESPA_DLL_LOCAL static T computeDotProduct(const T * af, const T * bf, size_t sz) __attribute__((noinline));
};

}
}
