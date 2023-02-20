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
    Range range = sanitize(org.range());
    if (range.validNonZero()) {
        const Word *startWord = org.getWordIndex(range.start());
        size_t numBytes2Copy = numActiveBytes(range.start(), range.end());
        memcpy(getWordIndex(range.start()), startWord, numBytes2Copy);
        if (range.end() < end) {
            clearInterval(range.end(), end);
        }
        if (start < range.start()) {
            clearInterval(start, range.start());
        }
    } else {
        clear();
    }
    set_bit_no_range_check(size());
}

PartialBitVector::~PartialBitVector() = default;

} // namespace search
