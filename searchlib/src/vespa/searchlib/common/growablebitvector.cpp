// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include "growablebitvector.h"

/////////////////////////////////
namespace search {

using vespalib::GenerationHeldBase;
using vespalib::GenerationHolder;

GrowableBitVector::GrowableBitVector(Index newSize,
                                     Index newCapacity,
                                     GenerationHolder &generationHolder)
    : AllocatedBitVector(newSize, newCapacity, nullptr, 0),
      _generationHolder(generationHolder)
{
    assert(newSize <= newCapacity);
}

void
GrowableBitVector::reserve(Index newCapacity)
{
    Index oldCapacity = capacity();
    assert(newCapacity >= oldCapacity);
    if (newCapacity == oldCapacity)
        return;
    hold(grow(size(), newCapacity));
}

void GrowableBitVector::hold(GenerationHeldBase::UP v)
{
    if (v) {
        _generationHolder.hold(std::move(v));
    }
}

void
GrowableBitVector::shrink(Index newCapacity)
{
    Index oldCapacity = capacity();
    assert(newCapacity <= oldCapacity);
    (void) oldCapacity;
    hold(grow(newCapacity, std::max(capacity(), newCapacity)));
}

void
GrowableBitVector::extend(Index newCapacity)
{
    hold(grow(newCapacity, std::max(capacity(), newCapacity)));
}

} // namespace search
