// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "load_utils.hpp"

namespace search
{

template <typename T, typename I>
template <typename V, class Saver>
uint32_t
MultiValueMappingT<T, I>::fillMapped(AttributeVector::ReaderBase &attrReader,
                                     uint64_t numValues,
                                     const V *map,
                                     size_t mapSize,
                                     Saver &saver,
                                     uint32_t numDocs,
                                     bool hasWeights)
{
    (void) numValues;
    (void) hasWeights;
    Histogram capacityNeeded = this->getHistogram(attrReader);
    reset(numDocs, capacityNeeded);
    attrReader.rewind();
    using Map = vespalib::ConstArrayRef<V>;
    return attribute::loadFromEnumeratedMultiValue(*this, attrReader,
                                                   Map(map, mapSize),
                                                   saver);
}

} // namespace search

