// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "load_utils.hpp"

namespace search
{

template <typename T, typename I>
template <typename V, class Saver>
uint32_t
MultiValueMappingT<T, I>::fillMapped(AttributeVector::ReaderBase &attrReader,
                                     vespalib::ConstArrayRef<V> enumValueToValueMap,
                                     Saver saver)
{
    uint32_t numDocs = attrReader.getNumIdx() - 1;
    Histogram capacityNeeded = this->getHistogram(attrReader);
    reset(numDocs, capacityNeeded);
    attrReader.rewind();
    return attribute::loadFromEnumeratedMultiValue(*this, attrReader, enumValueToValueMap, saver);
}

} // namespace search

