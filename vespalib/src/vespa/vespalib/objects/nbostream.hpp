// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "nbostream.h"
#include <cassert>

namespace vespalib {

template <typename T>
nbostream &
nbostream::saveVector(const std::vector<T> &val)
{
    size_t valCapacity = val.capacity();
    size_t valSize = val.size();
    assert(valCapacity >= valSize);
    *this << valCapacity << valSize;
    for (const T & v : val) {
        *this << v;
    }
    return *this;
}

template <typename T>
nbostream &
nbostream::restoreVector(std::vector<T> &val)
{
    size_t valCapacity = 0;
    size_t valSize = 0;
    *this >> valCapacity >> valSize;
    assert(valCapacity >= valSize);
    val.reserve(valCapacity);
    val.clear();
    T i;
    for (size_t j = 0; j < valSize; ++j) {
        *this >> i;
        val.push_back(i);
    }
    return *this;
}

}
