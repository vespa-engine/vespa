// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "partialbitvector.h"
#include <cstring>

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
    const Word * startWord = org.getWordIndex(start);
    size_t numBytes2Copy = numActiveBytes(start, std::min(org.size(), end));
    memcpy(_alloc.get(), startWord, numBytes2Copy);
    if (org.size() < end) {
        clearInterval(org.size(), end);
    }
    setBit(size());
}

PartialBitVector::~PartialBitVector() = default;

} // namespace search
