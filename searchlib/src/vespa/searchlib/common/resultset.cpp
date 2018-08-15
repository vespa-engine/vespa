// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultset.h"
#include "bitvector.h"
#include <cstring>

using vespalib::alloc::Alloc;

namespace search {

//Above 32M we hand back to the OS directly.
constexpr size_t MMAP_LIMIT = 0x2000000;

ResultSet::ResultSet()
    : _elemsUsedInRankedHitsArray(0u),
      _rankedHitsArrayAllocElements(0u),
      _bitOverflow(),
      _rankedHitsArray()
{}

ResultSet::~ResultSet() = default;


void
ResultSet::allocArray(unsigned int arrayAllocated)
{
    if (arrayAllocated > 0) {
        Alloc::alloc(arrayAllocated * sizeof(RankedHit), MMAP_LIMIT).swap(_rankedHitsArray);
    } else {
        Alloc().swap(_rankedHitsArray);
    }
    _rankedHitsArrayAllocElements = arrayAllocated;
    _elemsUsedInRankedHitsArray = 0;
}


void
ResultSet::setArrayUsed(unsigned int arrayUsed)
{
    assert(arrayUsed <= _rankedHitsArrayAllocElements);
    _elemsUsedInRankedHitsArray = arrayUsed;
}


void
ResultSet::setBitOverflow(BitVector::UP newBitOverflow)
{
    _bitOverflow = std::move(newBitOverflow);
}


//////////////////////////////////////////////////////////////////////
// Find number of hits
//////////////////////////////////////////////////////////////////////
unsigned int
ResultSet::getNumHits() const
{
    return (_bitOverflow) ? _bitOverflow->countTrueBits() : _elemsUsedInRankedHitsArray;
}


void
ResultSet::mergeWithBitOverflow(HitRank default_value)
{
    if ( ! _bitOverflow) {
        return;
    }

    const BitVector *bitVector = _bitOverflow.get();

    const RankedHit *oldA     = getArray();
    const RankedHit *oldAEnd  = oldA + _elemsUsedInRankedHitsArray;
    uint32_t        bidx     = bitVector->getFirstTrueBit();

    uint32_t  actualHits = getNumHits();
    Alloc newHitsAlloc = Alloc::alloc(actualHits*sizeof(RankedHit), MMAP_LIMIT);
    RankedHit *newHitsArray = static_cast<RankedHit *>(newHitsAlloc.get());

    RankedHit * tgtA    = newHitsArray;
    RankedHit * tgtAEnd = newHitsArray + actualHits;

    if (oldAEnd > oldA) { // we have array hits
        uint32_t firstArrayHit = oldA->_docId;
        uint32_t lastArrayHit  = (oldAEnd - 1)->_docId;

        // bitvector hits before array hits
        while (bidx < firstArrayHit) {
            tgtA->_docId = bidx;
            tgtA->_rankValue = default_value;
            tgtA++;
            bidx = bitVector->getNextTrueBit(bidx + 1);
        }

        // merge bitvector and array hits
        while (bidx <= lastArrayHit) {
            tgtA->_docId = bidx;
            if (bidx == oldA->_docId) {
                tgtA->_rankValue = oldA->_rankValue;
                oldA++;
            } else {
                tgtA->_rankValue = default_value;
            }
            tgtA++;
            bidx = bitVector->getNextTrueBit(bidx + 1);
        }
    }
    assert(oldA == oldAEnd);

    // bitvector hits after array hits
    while (tgtA < tgtAEnd) {
        tgtA->_docId = bidx;
        tgtA->_rankValue = default_value;
        tgtA++;
        bidx = bitVector->getNextTrueBit(bidx + 1);
    }
    _rankedHitsArrayAllocElements =  actualHits;
    _elemsUsedInRankedHitsArray =  actualHits;
    _rankedHitsArray.swap(newHitsAlloc);
    setBitOverflow(nullptr);
}

} // namespace search
