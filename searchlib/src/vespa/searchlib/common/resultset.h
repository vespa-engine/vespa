// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/searchlib/common/rankedhit.h>
#include <vespa/searchlib/common/bitvector.h>

namespace search
{

class ResultSet
{
private:
    ResultSet& operator=(const ResultSet &);

    unsigned int _elemsUsedInRankedHitsArray;
    unsigned int _rankedHitsArrayAllocElements;
    BitVector::UP          _bitOverflow;
    vespalib::alloc::Alloc _rankedHitsArray;

public:
    typedef std::unique_ptr<ResultSet> UP;
    typedef std::shared_ptr<ResultSet> SP;
    ResultSet(void);
    ResultSet(const ResultSet &);  // Used only for testing .....
    virtual ~ResultSet(void);

    void allocArray(unsigned int arrayAllocated);

    void setArrayUsed(unsigned int arrayUsed);
    void setBitOverflow(BitVector::UP newBitOverflow);
    const RankedHit * getArray(void) const { return static_cast<const RankedHit *>(_rankedHitsArray.get()); }
    RankedHit *       getArray(void)       { return static_cast<RankedHit *>(_rankedHitsArray.get()); }
    unsigned int      getArrayUsed(void) const { return _elemsUsedInRankedHitsArray; }
    unsigned int getArrayAllocated(void) const { return _rankedHitsArrayAllocElements; }

    const BitVector * getBitOverflow(void) const { return _bitOverflow.get(); }
    BitVector *       getBitOverflow(void)       { return _bitOverflow.get(); }
    unsigned int getNumHits(void) const;
    void mergeWithBitOverflow(void);

    /* isEmpty() is allowed to return false even if bitmap has no hits */
    bool isEmpty(void) const { return (_bitOverflow == NULL && _elemsUsedInRankedHitsArray == 0); }
};

} // namespace search

