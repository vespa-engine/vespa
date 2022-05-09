// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "growablebitvector.h"
#include <cassert>

/////////////////////////////////
namespace search {

using vespalib::GenerationHeldBase;
using vespalib::GenerationHeldAlloc;
using vespalib::GenerationHolder;

GenerationHeldBase::UP
GrowableBitVector::grow(Index newSize, Index newCapacity)
{
    assert(newCapacity >= newSize);
    GenerationHeldBase::UP ret;
    if (newCapacity != capacity()) {
        AllocatedBitVector tbv(newSize, newCapacity, _alloc.get(), size(), &_alloc);
        if (newSize > size()) {
            tbv.clearBitAndMaintainCount(size());  // Clear old guard bit.
        }
        ret = std::make_unique<GenerationHeldAlloc<Alloc>>(_alloc);
        swap(tbv);
    } else {
        if (newSize > size()) {
            Range clearRange(size(), newSize);
            setSize(newSize);
            clearIntervalNoInvalidation(clearRange);
        } else {
            clearIntervalNoInvalidation(Range(newSize, size()));
            setSize(newSize);
            updateCount();
        }
    }
    return ret;
}

GrowableBitVector::GrowableBitVector(Index newSize, Index newCapacity,
                                     GenerationHolder &generationHolder,
                                     const Alloc* init_alloc)
    : AllocatedBitVector(newSize, newCapacity, nullptr, 0, init_alloc),
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
