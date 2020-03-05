// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generic.h"

namespace vespalib::hwaccelrated {

/**
 * Generic cpu agnostic implementation.
 */
class Sse2Accelrator : public GenericAccelrator
{
public:
    size_t populationCount(const uint64_t *a, size_t sz) const override;
};

}
