// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankedhit.h"
#include <vespa/vespalib/util/alloc.h>

namespace search {

class BitVector;

class ResultSet
{
private:
    unsigned int _elemsUsedInRankedHitsArray;
    unsigned int _rankedHitsArrayAllocElements;
    std::unique_ptr<BitVector> _bitOverflow;
    vespalib::alloc::Alloc     _rankedHitsArray;
public:
    using UP = std::unique_ptr<ResultSet>;
    ResultSet& operator=(const ResultSet &) = delete;
    ResultSet(const ResultSet &) = delete;
    ResultSet();
    ~ResultSet();

    void allocArray(unsigned int arrayAllocated);

    void setArrayUsed(unsigned int arrayUsed);
    void setBitOverflow(std::unique_ptr<BitVector> newBitOverflow);
    const RankedHit * getArray() const { return static_cast<const RankedHit *>(_rankedHitsArray.get()); }
    RankedHit *       getArray()       { return static_cast<RankedHit *>(_rankedHitsArray.get()); }
    unsigned int      getArrayUsed() const { return _elemsUsedInRankedHitsArray; }
    unsigned int getArrayAllocated() const { return _rankedHitsArrayAllocElements; }

    const BitVector * getBitOverflow() const { return _bitOverflow.get(); }
    BitVector *       getBitOverflow()       { return _bitOverflow.get(); }
    unsigned int getNumHits() const;
    void mergeWithBitOverflow(HitRank default_value = default_rank_value);
};

} // namespace search
