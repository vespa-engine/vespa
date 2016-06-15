// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/vespalib/hwaccelrated/generic.h>

namespace vespalib {

namespace hwaccelrated {

/**
 * Generic cpu agnostic implementation.
 */
class Sse2Accelrator : public GenericAccelrator
{
public:
    virtual float dotProduct(const float * a, const float * b, size_t sz) const;
    virtual double dotProduct(const double * a, const double * b, size_t sz) const;
};

}
}
