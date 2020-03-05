// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sse2.h"
#include "private_helpers.hpp"

namespace vespalib::hwaccelrated {

size_t
Sse2Accelrator::populationCount(const uint64_t *a, size_t sz) const {
    return helper::populationCount(a, sz);
}

}
