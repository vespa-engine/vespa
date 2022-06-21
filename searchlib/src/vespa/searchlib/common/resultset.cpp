// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultset.h"
#include "bitvector.h"
#include "sortresults.h"
#include <cstring>
#include <cassert>

using vespalib::alloc::Alloc;

namespace search {

//Above 32M we hand back to the OS directly.
constexpr size_t MMAP_LIMIT = 0x2000000;

ResultSet::ResultSet()
    : _bitOverflow(),
      _rankedHitsArray(Alloc::alloc(0, MMAP_LIMIT))
{}

ResultSet::~ResultSet() = default;


void
ResultSet::allocArray(unsigned int arrayAllocated)
{
    if (arrayAllocated > 0) {
        _rankedHitsArray.reserve(arrayAllocated);
    } else {
        _rankedHitsArray.clear();
    }
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
    return (_bitOverflow) ? _bitOverflow->countTrueBits() : _rankedHitsArray.size();
}


void
ResultSet::mergeWithBitOverflow(HitRank default_value)
{
    if ( ! _bitOverflow) {
        return;
    }

    const BitVector *bitVector = _bitOverflow.get();

    const RankedHit *oldA     = getArray();
    const RankedHit *oldAEnd  = oldA + getArrayUsed();
    uint32_t        bidx     = bitVector->getFirstTrueBit();

    uint32_t  actualHits = getNumHits();
    vespalib::Array<RankedHit>  newHits(Alloc::alloc(0, MMAP_LIMIT));
    newHits.reserve(actualHits);

    if (oldAEnd > oldA) { // we have array hits
        uint32_t firstArrayHit = oldA->_docId;
        uint32_t lastArrayHit  = (oldAEnd - 1)->_docId;

        // bitvector hits before array hits
        while (bidx < firstArrayHit) {
            newHits.push_back_fast(RankedHit(bidx, default_value));
            bidx = bitVector->getNextTrueBit(bidx + 1);
        }

        // merge bitvector and array hits
        while (bidx <= lastArrayHit) {
            if (bidx == oldA->_docId) {
                newHits.push_back_fast(RankedHit(bidx, oldA->_rankValue));
                oldA++;
            } else {
                newHits.push_back_fast(RankedHit(bidx, default_value));
            }
            bidx = bitVector->getNextTrueBit(bidx + 1);
        }
    }
    assert(oldA == oldAEnd);

    // bitvector hits after array hits
    while (newHits.size() < actualHits) {
        newHits.push_back_fast(RankedHit(bidx, default_value));
        bidx = bitVector->getNextTrueBit(bidx + 1);
    }
    _rankedHitsArray.swap(newHits);
    setBitOverflow(nullptr);
}

void
ResultSet::sort(FastS_IResultSorter & sorter, unsigned int ntop) {
    sorter.sortResults(_rankedHitsArray.data(), _rankedHitsArray.size(), ntop);
}

std::pair<std::unique_ptr<BitVector>, vespalib::Array<RankedHit>>
ResultSet::copyResult() const {
    std::unique_ptr<BitVector> copy = _bitOverflow ? BitVector::create(*_bitOverflow) : std::unique_ptr<BitVector>();
    return std::make_pair(std::move(copy), _rankedHitsArray);
}

std::pair<std::unique_ptr<BitVector>, vespalib::Array<RankedHit>>
ResultSet::stealResult(ResultSet && rhs) {
    return std::make_pair(std::move(rhs._bitOverflow), std::move(rhs._rankedHitsArray));
}

} // namespace search
