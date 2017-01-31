// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "allocatedbitvector.h"

namespace search {

using vespalib::nbostream;
using vespalib::GenerationHeldBase;
using vespalib::GenerationHeldAlloc;
using vespalib::GenerationHolder;

//////////////////////////////////////////////////////////////////////
// Parameterized Constructor
//////////////////////////////////////////////////////////////////////
AllocatedBitVector::AllocatedBitVector(Index numberOfElements) :
    BitVector(),
    _capacityBits(numberOfElements),
    _alloc(allocatePaddedAndAligned(numberOfElements))
{
    init(_alloc.get(), 0, numberOfElements);
    clear();
}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements, Alloc buffer, size_t offset) :
    BitVector(static_cast<char *>(buffer.get()) + offset, numberOfElements),
    _capacityBits(numberOfElements),
    _alloc(std::move(buffer))
{
}

AllocatedBitVector::AllocatedBitVector(Index numberOfElements, Index capacityBits, const void * rhsBuf, size_t rhsSize) :
    BitVector(),
    _capacityBits(capacityBits),
    _alloc(allocatePaddedAndAligned(0, numberOfElements, capacityBits))
{
    init(_alloc.get(), 0, numberOfElements);
    clear();
    if (rhsSize > 0) {
        size_t minCount = std::min(static_cast<size_t>(numberOfElements), rhsSize);
        memcpy(getStart(), rhsBuf, numBytes(minCount));
        if (minCount/8 == numberOfElements/8) {
            static_cast<Word *>(getStart())[numWords()-1] &= ~endBits(minCount);
        }
        setBit(size()); // Guard bit
    }
}

AllocatedBitVector::AllocatedBitVector(const AllocatedBitVector & rhs) :
    AllocatedBitVector(rhs, rhs.capacity())
{ }

AllocatedBitVector::AllocatedBitVector(const BitVector & rhs) :
    AllocatedBitVector(rhs, rhs.size())
{ }

AllocatedBitVector::AllocatedBitVector(const BitVector & rhs, Index capacity_) :
    BitVector(),
    _capacityBits(capacity_),
    _alloc(allocatePaddedAndAligned(0, rhs.size(), capacity_))
{
    memcpy(_alloc.get(),  rhs.getStart(), rhs.sizeBytes());
    init(_alloc.get(), 0, rhs.size());
}

//////////////////////////////////////////////////////////////////////
// Destructor
//////////////////////////////////////////////////////////////////////
AllocatedBitVector::~AllocatedBitVector() { }

void
AllocatedBitVector::cleanup()
{
    init(nullptr, 0, 0);
    Alloc().swap(_alloc);
    _capacityBits = 0;
}

void
AllocatedBitVector::resize(Index newLength)
{
    _capacityBits = newLength;
    _alloc = allocatePaddedAndAligned(_capacityBits);
    init(_alloc.get(), 0, newLength);
    clear();
}

AllocatedBitVector &
AllocatedBitVector::operator=(const AllocatedBitVector & rhs)
{
    AllocatedBitVector tmp(rhs);
    swap(tmp);
    assert(testBit(size()));

    return *this;
}
AllocatedBitVector &
AllocatedBitVector::operator=(const BitVector & rhs)
{
    AllocatedBitVector tmp(rhs);
    swap(tmp);
    assert(testBit(size()));

    return *this;
}

GenerationHeldBase::UP
AllocatedBitVector::grow(Index newSize, Index newCapacity)
{
    assert(newCapacity >= newSize);
    GenerationHeldBase::UP ret;
    if (newCapacity != capacity()) {
        AllocatedBitVector tbv(newSize, newCapacity, _alloc.get(), size());
        if (newSize > size()) {
            tbv.clearBit(size());  // Clear old guard bit.
        }
        ret.reset(new GenerationHeldAlloc<Alloc>(_alloc));
        if (( newSize >= size()) && isValidCount()) {
            tbv.setTrueBits(countTrueBits());
        }
        swap(tbv);
    } else {
        if (newSize > size()) {
            Index oldSz(size());
            setSize(newSize);
            clearIntervalNoInvalidation(oldSz, newSize);
        } else {
            clearInterval(newSize, size());
            setSize(newSize);
        }
    }
    return ret;
}

} // namespace search
