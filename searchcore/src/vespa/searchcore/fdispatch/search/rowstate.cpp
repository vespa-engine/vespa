// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/fdispatch/search/rowstate.h>

namespace fdispatch {

constexpr uint64_t MIN_DECAY_RATE = 42;
constexpr double MIN_QUERY_TIME = 0.001;

RowState::RowState(double initialValue, uint64_t decayRate) :
    _decayRate(std::max(decayRate, MIN_DECAY_RATE)),
    _avgSearchTime(std::max(initialValue, MIN_QUERY_TIME)),
    _sumActiveDocs(0),
    _numQueries(0)
{ }

void RowState::updateSearchTime(double searchTime)
{
    searchTime = std::max(searchTime, MIN_QUERY_TIME);
    double decayRate = std::min(_numQueries + MIN_DECAY_RATE, _decayRate);
    _avgSearchTime = (searchTime + (decayRate-1)*_avgSearchTime)/decayRate;
    ++_numQueries;
}

StateOfRows::StateOfRows(size_t numRows, double initialValue, uint64_t decayRate) :
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
