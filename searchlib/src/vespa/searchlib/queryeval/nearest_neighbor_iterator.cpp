// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearest_neighbor_iterator.h"
#include <vespa/searchlib/common/bitvector.h>

using search::tensor::DenseTensorAttribute;
using vespalib::ConstArrayRef;
using vespalib::eval::TypedCells;
using vespalib::eval::CellType;

namespace search::queryeval {

namespace {

bool
is_compatible(const vespalib::eval::ValueType& lhs,
              const vespalib::eval::ValueType& rhs)
{
    return (lhs == rhs);
}

}

/**
 * Search iterator for K nearest neighbor matching.
 * Uses unpack() as feedback mechanism to track which matches actually became hits.
 * Keeps a heap of the K best hit distances.
 * Currently always does brute-force scanning, which is very expensive.
 **/
template <bool strict, bool has_filter>
class NearestNeighborImpl : public NearestNeighborIterator
{
public:

    NearestNeighborImpl(Params params_in)
        : NearestNeighborIterator(params_in),
          _lhs(params().queryTensor.cells()),
          _lastScore(0.0)
    {
        assert(is_compatible(params().tensorAttribute.getTensorType(),
                             params().queryTensor.type()));
    }

    ~NearestNeighborImpl();

    void doSeek(uint32_t docId) override {
        double distanceLimit = params().distanceHeap.distanceLimit();
        while (__builtin_expect((docId < getEndId()), true)) {
            if ((!has_filter) || params().filter->testBit(docId)) {
                double d = computeDistance(docId, distanceLimit);
                if (d <= distanceLimit) {
                    _lastScore = d;
                    setDocId(docId);
                    return;
                }
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
        double score = params().distanceFunction->to_rawscore(_lastScore);
        params().tfmd.setRawScore(docId, score);
        params().distanceHeap.used(_lastScore);
    }

    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False ; }

private:
    double computeDistance(uint32_t docId, double limit) {
        auto rhs = params().tensorAttribute.extract_cells_ref(docId);
        return params().distanceFunction->calc_with_limit(_lhs, rhs, limit);
    }

    TypedCells             _lhs;
    double                 _lastScore;
};

template <bool strict, bool has_filter>
NearestNeighborImpl<strict, has_filter>::~NearestNeighborImpl() = default;

namespace {

template <bool has_filter>
std::unique_ptr<NearestNeighborIterator>
resolve_strict(bool strict, const NearestNeighborIterator::Params &params)
{
    CellType lct = params.queryTensor.type().cell_type();
    CellType rct = params.tensorAttribute.getTensorType().cell_type();
    if (lct != rct) abort();
    if (strict) {
        using NNI = NearestNeighborImpl<true, has_filter>;
        return std::make_unique<NNI>(params);
    } else {
        using NNI = NearestNeighborImpl<false, has_filter>;
        return std::make_unique<NNI>(params);
    }
}

} // namespace <unnamed>

std::unique_ptr<NearestNeighborIterator>
NearestNeighborIterator::create(
        bool strict,
        fef::TermFieldMatchData &tfmd,
        const vespalib::eval::Value &queryTensor,
        const search::tensor::DenseTensorAttribute &tensorAttribute,
        NearestNeighborDistanceHeap &distanceHeap,
        const search::BitVector *filter,
        const search::tensor::DistanceFunction *dist_fun)

{
    Params params(tfmd, queryTensor, tensorAttribute, distanceHeap, filter, dist_fun);
    if (filter) {
        return resolve_strict<true>(strict, params);
    } else  {
        return resolve_strict<false>(strict, params);
    }
}

} // namespace
