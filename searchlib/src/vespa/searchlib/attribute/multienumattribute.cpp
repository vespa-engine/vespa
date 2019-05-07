// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multienumattribute.h"
#include "multienumattribute.hpp"
#include <stdexcept>

namespace search {

uint32_t
IWeightedIndexVector::getEnumHandles(uint32_t, const WeightedIndex * &) const {
    throw std::runtime_error("IWeightedIndexVector::getEnumHandles() not implmented");
}

} // namespace search

