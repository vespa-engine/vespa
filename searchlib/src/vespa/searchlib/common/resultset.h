// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "rankedhit.h"
#include <vespa/vespalib/util/array.h>

struct FastS_IResultSorter;

namespace search {

class BitVector;

class ResultSet
{
private:
    std::unique_ptr<BitVector> _bitOverflow;
    vespalib::Array<RankedHit> _rankedHitsArray;
public:
    using UP = std::unique_ptr<ResultSet>;
    ResultSet& operator=(const ResultSet &) = delete;
    ResultSet(const ResultSet &) = delete;
    ResultSet();
    ~ResultSet();

    void allocArray(unsigned int arrayAllocated);

    void setBitOverflow(std::unique_ptr<BitVector> newBitOverflow);
    const RankedHit * getArray() const { return _rankedHitsArray.data(); }
    RankedHit & operator [](uint32_t i) { return _rankedHitsArray[i]; }
    void push_back(RankedHit hit) { _rankedHitsArray.push_back_fast(hit); }
    unsigned int      getArrayUsed() const { return _rankedHitsArray.size(); }

    const BitVector * getBitOverflow() const { return _bitOverflow.get(); }
    BitVector *       getBitOverflow()       { return _bitOverflow.get(); }
    unsigned int getNumHits() const;
    void mergeWithBitOverflow(HitRank default_value = default_rank_value);
    void sort(FastS_IResultSorter & sorter, unsigned int ntop);
    std::pair<std::unique_ptr<BitVector>, vespalib::Array<RankedHit>> copyResult() const;
    static std::pair<std::unique_ptr<BitVector>, vespalib::Array<RankedHit>>
    stealResult(ResultSet && rhs);
};

} // namespace search
