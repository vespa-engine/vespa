// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/searchcore/fdispatch/search/rowstate.h>

namespace fdispatch {

void RowState::updateSearchTime(double searchTime)
{
    _avgSearchTime = (searchTime + (_decayRate-1)*_avgSearchTime)/_decayRate;
}

StateOfRows::StateOfRows(size_t numRows, double initialValue, double decayRate) :
   _rows(numRows, RowState(initialValue, decayRate)),
   _sumActiveDocs(0), _invalidActiveDocsCounter(0)
{
   srand48(1);
}

void
StateOfRows::updateSearchTime(double searchTime, uint32_t rowId)
{
    _rows[rowId].updateSearchTime(searchTime);
}

uint32_t
StateOfRows::getRandomWeightedRow() const
{
    return getWeightedNode(drand48());
}

uint32_t
StateOfRows::getWeightedNode(double cand) const
{
    double sum = 0;
    for (const RowState & rs : _rows) {
        sum += rs.getAverageSearchTimeInverse();
    }
    double accum(0.0);
    for (size_t rowId(0); (rowId + 1) < _rows.size(); rowId++) {
        accum += _rows[rowId].getAverageSearchTimeInverse();
        if (cand < accum/sum) {
            return rowId;
        }
    }
    return _rows.size() - 1;
}

void
StateOfRows::updateActiveDocs(uint32_t rowId, PossCount newVal, PossCount oldVal)
{
    uint64_t tmp = _sumActiveDocs + newVal.count - oldVal.count;
    _sumActiveDocs = tmp;
    _rows[rowId].updateActiveDocs(newVal.count, oldVal.count);
    if (newVal.valid != oldVal.valid) {
        if (oldVal.valid) {
            ++_invalidActiveDocsCounter;
        } else {
            --_invalidActiveDocsCounter;
        }
    }
}

PossCount
StateOfRows::getActiveDocs() const
{
    PossCount r;
    if (activeDocsValid()) {
        r.valid = true;
        r.count = 0;
        for (const RowState &row : _rows) {
            r.count = std::max(r.count, row.activeDocs());
        }
    }
    return r;
}

}
