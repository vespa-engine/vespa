// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "partialbitvector.h"

namespace search {

using vespalib::alloc::Alloc;

PartialBitVector::PartialBitVector(Index start, Index end) :
    BitVector(),
    _alloc(allocatePaddedAndAligned(start, end))
{
    init(_alloc.get(), start, end);
    clear();
}

PartialBitVector::PartialBitVector(const BitVector & org, Index start, Index end) :
    BitVector(),
    _alloc(allocatePaddedAndAligned(start, end))
{
    init(_alloc.get(), start, end);
    memcpy(_alloc.get(), org.getWordIndex(start), numActiveBytes(start, end));
    setBit(size());
}

PartialBitVector::~PartialBitVector() { }

} // namespace search
