// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_iterator.h"

using search::tensor::DenseTensorAttribute;
using vespalib::ConstArrayRef;
using vespalib::tensor::DenseTensorView;
using vespalib::tensor::MutableDenseTensorView;
using vespalib::tensor::TypedCells;

using CellType = vespalib::eval::ValueType::CellType;

namespace search::queryeval {

namespace {

bool
is_compatible(const vespalib::eval::ValueType& lhs,
              const vespalib::eval::ValueType& rhs)
{
    return (lhs.dimensions() == rhs.dimensions());
}

}

/**
 * Search iterator for K nearest neighbor matching.
 * Uses unpack() as feedback mechanism to track which matches actually became hits.
 * Keeps a heap of the K best hit distances.
 * Currently always does brute-force scanning, which is very expensive.
 **/
template <bool strict, typename LCT, typename RCT>
class NearestNeighborImpl : public NearestNeighborIterator
{
public:

    NearestNeighborImpl(Params params_in)
        : NearestNeighborIterator(params_in),
          _lhs(params().queryTensor.cellsRef().template typify<LCT>()),
          _fieldTensor(params().tensorAttribute.getTensorType()),
          _lastScore(0.0)
    {
        assert(is_compatible(_fieldTensor.fast_type(), params().queryTensor.fast_type()));
    }

    ~NearestNeighborImpl();

    void doSeek(uint32_t docId) override {
        double distanceLimit = params().distanceHeap.distanceLimit();
        while (__builtin_expect((docId < getEndId()), true)) {
            double d = computeDistance(docId, distanceLimit);
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
        params().tfmd.setRawScore(docId, sqrt(_lastScore));
        params().distanceHeap.used(_lastScore);
    }

    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False ; }

private:
    static double computeSum(ConstArrayRef<LCT> lhs, ConstArrayRef<RCT> rhs, double limit) {
        double sum = 0.0;
        size_t sz = lhs.size();
        assert(sz == rhs.size());
        for (size_t i = 0; i < sz && sum <= limit; ++i) {
            double diff = lhs[i] - rhs[i];
            sum += diff*diff;
        }
        return sum;
    }

    double computeDistance(uint32_t docId, double limit) {
        params().tensorAttribute.getTensor(docId, _fieldTensor);
        return computeSum(_lhs, _fieldTensor.cellsRef().template typify<RCT>(), limit);
    }

    ConstArrayRef<LCT>     _lhs;
    MutableDenseTensorView _fieldTensor;
    double                 _lastScore;
};

template <bool strict, typename LCT, typename RCT>
NearestNeighborImpl<strict, LCT, RCT>::~NearestNeighborImpl() = default;

namespace {

template<bool strict, typename LCT, typename RCT>
std::unique_ptr<NearestNeighborIterator>
create_impl(const NearestNeighborIterator::Params &params)
{
    using NNI = NearestNeighborImpl<strict, LCT, RCT>;
    return std::make_unique<NNI>(params);
}

template<bool strict, typename LCT>
std::unique_ptr<NearestNeighborIterator>
resolve_RCT(const NearestNeighborIterator::Params &params)
{
    CellType ct = params.tensorAttribute.getTensorType().cell_type();
    if (ct == CellType::FLOAT) {
        return create_impl<strict, LCT, float>(params);
    }
    if (ct == CellType::DOUBLE) {
        return create_impl<strict, LCT, double>(params);
    }
    abort();
}

template<bool strict>
std::unique_ptr<NearestNeighborIterator>
resolve_LCT_RCT(const NearestNeighborIterator::Params &params)
{
    CellType ct = params.queryTensor.fast_type().cell_type();
    if (ct == CellType::FLOAT) {
        return resolve_RCT<strict, float>(params);
    }
    if (ct == CellType::DOUBLE) {
        return resolve_RCT<strict, double>(params);
    }
    abort();
}

std::unique_ptr<NearestNeighborIterator>
resolve_strict_LCT_RCT(bool strict, const NearestNeighborIterator::Params &params)
{
    if (strict) {
        return resolve_LCT_RCT<true>(params);
    } else {
        return resolve_LCT_RCT<false>(params);
    }
}

} // namespace <unnamed>

std::unique_ptr<NearestNeighborIterator>
NearestNeighborIterator::create(
        bool strict,
        fef::TermFieldMatchData &tfmd,
        const vespalib::tensor::DenseTensorView &queryTensor,
        const search::tensor::DenseTensorAttribute &tensorAttribute,
        NearestNeighborDistanceHeap &distanceHeap)
{
    Params params(tfmd, queryTensor, tensorAttribute, distanceHeap);
    return resolve_strict_LCT_RCT(strict, params);
}

} // namespace
