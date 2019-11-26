// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_iterator.h"

using vespalib::ConstArrayRef;
using vespalib::tensor::TypedCells;

namespace {

struct SumSquaredDiff
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

/**
 * Search iterator for K nearest neighbor matching.
 * Uses unpack() as feedback mechanism to track which matches actually became hits.
 * Keeps a heap of the K best hit distances.
 * Currently always does brute-force scanning, which is very expensive.
 **/
template <bool strict>
class NearestNeighborIterator : public SearchIterator
{
public:
    using DenseTensorView = vespalib::tensor::DenseTensorView;
    using DenseTensorAttribute = search::tensor::DenseTensorAttribute;
    using MutableDenseTensorView = vespalib::tensor::MutableDenseTensorView;

    NearestNeighborIterator(fef::TermFieldMatchData &tfmd,
                            const DenseTensorView &queryTensor,
                            const DenseTensorAttribute &tensorAttribute,
                            NearestNeighborDistanceHeap &distanceHeap)
        : _tfmd(tfmd),
          _queryTensor(queryTensor),
          _tensorAttribute(tensorAttribute),
          _fieldTensor(_tensorAttribute.getTensorType()),
          _distanceHeap(distanceHeap),
          _lastScore(0.0)
    {
        assert(_fieldTensor.fast_type() == _queryTensor.fast_type());
    }

    ~NearestNeighborIterator();

    void doSeek(uint32_t docId) override {
        double distanceLimit = _distanceHeap.distanceLimit();
        while (__builtin_expect((docId < getEndId()), true)) {
            double d = computeDistance(docId);
            if (d <= distanceLimit) {
                _lastScore = d;
                setDocId(docId);
                return;
            }
            if (strict) {
                ++docId;
            } else {
                return;
            }
        }
        setAtEnd();
    }

    void doUnpack(uint32_t docId) override {
        _tfmd.setRawScore(docId, sqrt(_lastScore));
        _distanceHeap.used(_lastScore);
    }

    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False ; }

private:
    double computeDistance(uint32_t docId);

    fef::TermFieldMatchData       &_tfmd;
    const DenseTensorView         &_queryTensor;
    const DenseTensorAttribute    &_tensorAttribute;
    MutableDenseTensorView         _fieldTensor;
    NearestNeighborDistanceHeap   &_distanceHeap;
    double                         _lastScore;
};

template <bool strict>
NearestNeighborIterator<strict>::~NearestNeighborIterator() = default;

template <bool strict>
double
NearestNeighborIterator<strict>::computeDistance(uint32_t docId)
{
    _tensorAttribute.getTensor(docId, _fieldTensor);
    TypedCells lhsCells = _queryTensor.cellsRef();
    TypedCells rhsCells = _fieldTensor.cellsRef();
    return vespalib::tensor::dispatch_2<SumSquaredDiff>(lhsCells, rhsCells);
}


std::unique_ptr<SearchIterator>
NearestNeighborIteratorFactory::createIterator(
        bool strict,
        fef::TermFieldMatchData &tfmd,
        const vespalib::tensor::DenseTensorView &queryTensor,
        const search::tensor::DenseTensorAttribute &tensorAttribute,
        NearestNeighborDistanceHeap &distanceHeap)
{
    using StrictNN = NearestNeighborIterator<true>;
    using UnStrict = NearestNeighborIterator<false>;

    if (strict) {
        return std::make_unique<StrictNN>(tfmd, queryTensor, tensorAttribute, distanceHeap);
    } else {
        return std::make_unique<UnStrict>(tfmd, queryTensor, tensorAttribute, distanceHeap);
    }
}

} // namespace
