// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankedhit.h"
#include <vespa/vespalib/util/alloc.h>

namespace search {

class BitVector;

class ResultSet
{
private:
    ResultSet& operator=(const ResultSet &);

    unsigned int _elemsUsedInRankedHitsArray;
    unsigned int _rankedHitsArrayAllocElements;
    std::unique_ptr<BitVector> _bitOverflow;
    vespalib::alloc::Alloc     _rankedHitsArray;

public:
    typedef std::unique_ptr<ResultSet> UP;
    typedef std::shared_ptr<ResultSet> SP;
    ResultSet();
    ResultSet(const ResultSet &);  // Used only for testing .....
    virtual ~ResultSet();

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
    void mergeWithBitOverflow();

    /* isEmpty() is allowed to return false even if bitmap has no hits */
    bool isEmpty() const { return (_bitOverflow == NULL && _elemsUsedInRankedHitsArray == 0); }
};

} // namespace search
