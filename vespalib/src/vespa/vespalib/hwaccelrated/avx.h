// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "sse2.h"

namespace vespalib::hwaccelrated {

/**
 * Avx-256 implementation.
 */
class AvxAccelrator : public Sse2Accelrator
{
public:
    size_t populationCount(const uint64_t *a, size_t sz) const override;
};

}
