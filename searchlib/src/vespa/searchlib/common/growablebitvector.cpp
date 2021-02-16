// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "growablebitvector.h"
#include <cassert>

/////////////////////////////////
namespace search {

using vespalib::GenerationHeldBase;
using vespalib::GenerationHolder;

GrowableBitVector::GrowableBitVector(Index newSize, Index newCapacity,
                                     GenerationHolder &generationHolder)
    : AllocatedBitVector(newSize, newCapacity, nullptr, 0),
      _generationHolder(generationHolder)
{
    assert(newSize <= newCapacity);
}

bool
GrowableBitVector::reserve(Index newCapacity)
{
    Index oldCapacity = capacity();
    assert(newCapacity >= oldCapacity);
    if (newCapacity == oldCapacity)
        return false;
    return hold(grow(size(), newCapacity));
}

bool
GrowableBitVector::hold(GenerationHeldBase::UP v)
{
    if (v) {
        _generationHolder.hold(std::move(v));
        return true;
    }
    return false;
}

bool
GrowableBitVector::shrink(Index newCapacity)
{
    Index oldCapacity = capacity();
    assert(newCapacity <= oldCapacity);
    (void) oldCapacity;
    return hold(grow(newCapacity, std::max(capacity(), newCapacity)));
}

bool
GrowableBitVector::extend(Index newCapacity)
{
    return hold(grow(newCapacity, std::max(capacity(), newCapacity)));
}

} // namespace search
