// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_iterator.h"

using vespalib::ConstArrayRef;
using vespalib::tensor::TypedCells;

namespace {

struct SumSq
{
    template <typename LCT, typename RCT>
    static double
    call(const ConstArrayRef<LCT> &lhs, const ConstArrayRef<RCT> &rhs)
    {
        double sum = 0.0;
        size_t sz = lhs.size();
        assert(sz == rhs.size());
        for (size_t i = 0; i < sz; ++i) {
            double diff = lhs[i] - rhs[i];
            sum += diff*diff;
        }
        return sum;
    }
};

}

namespace search::queryeval {

template <bool strict>
NearestNeighborIterator<strict>::~NearestNeighborIterator() = default;

template <bool strict>
double
NearestNeighborIterator<strict>::computeDistance(uint32_t docId)
{
    _tensorAttribute.getTensor(docId, _fieldTensor);
    TypedCells lhsCells = _queryTensor.cellsRef();
    TypedCells rhsCells = _fieldTensor.cellsRef();
    return vespalib::tensor::dispatch_2<SumSq>(lhsCells, rhsCells);
}

template class NearestNeighborIterator<false>;
template class NearestNeighborIterator<true>;

}
