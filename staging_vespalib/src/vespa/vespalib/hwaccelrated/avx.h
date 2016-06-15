// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/vespalib/hwaccelrated/sse2.h>
#include <vespa/fastos/dynamiclibrary.h>

namespace vespalib {

namespace hwaccelrated {

/**
 * Generic cpu agnostic implementation.
 */
class AvxAccelrator : public Sse2Accelrator
{
public:
    virtual float dotProduct(const float * a, const float * b, size_t sz) const;
    virtual double dotProduct(const double * a, const double * b, size_t sz) const;
private:
    template <typename T>
    VESPA_DLL_LOCAL static T dotProductSelectAlignment(const T * af, const T * bf, size_t sz);
    template <unsigned AlignA, unsigned AlignB>
    VESPA_DLL_LOCAL static double computeDotProduct(const double * af, const double * bf, size_t sz) __attribute__((noinline));
    template <unsigned AlignA, unsigned AlignB>
    VESPA_DLL_LOCAL static float computeDotProduct(const float * af, const float * bf, size_t sz) __attribute__((noinline));
};

}
}
