// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exact_nearest_neighbor_iterator.h"
#include "global_filter.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/tensor/distance_function.h>

using search::tensor::ITensorAttribute;
using vespalib::ConstArrayRef;
using vespalib::eval::TypedCells;
using vespalib::eval::CellType;

namespace search::queryeval {

/**
 * Search iterator for K nearest neighbor matching.
 * Uses unpack() as feedback mechanism to track which matches actually became hits.
 * Keeps a heap of the K best hit distances.
 * Currently always does brute-force scanning, which is very expensive.
 **/
template <bool strict, bool has_filter, bool has_single_subspace>
class ExactNearestNeighborImpl final : public ExactNearestNeighborIterator
{
public:
    explicit ExactNearestNeighborImpl(Params params_in)
        : ExactNearestNeighborIterator(std::move(params_in)),
          _lastScore(0.0)
    {
    }

    ~ExactNearestNeighborImpl() override;

    void doSeek(uint32_t docId) override {
        double distanceLimit = params().distanceHeap.distanceLimit();
        while (__builtin_expect((docId < getEndId()), true)) {
            if ((!has_filter) || params().filter.check(docId)) {
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
        double score = params().distance_calc->function().to_rawscore(_lastScore);
        params().tfmd.setRawScore(docId, score);
        params().distanceHeap.used(_lastScore);
    }

    Trinary is_strict() const override { return strict ? Trinary::True : Trinary::False ; }

private:
    double computeDistance(uint32_t docId, double limit) {
        return params().distance_calc->template calc_with_limit<has_single_subspace>(docId, limit);
    }

    double                 _lastScore;
};

template <bool strict, bool has_filter, bool has_single_subspace>
ExactNearestNeighborImpl<strict, has_filter, has_single_subspace>::~ExactNearestNeighborImpl() = default;

namespace {

template <bool strict, bool has_filter>
std::unique_ptr<ExactNearestNeighborIterator>
resolve_single_subspace(ExactNearestNeighborIterator::Params params)
{
    if (params.distance_calc->has_single_subspace()) {
        using NNI = ExactNearestNeighborImpl<strict, has_filter, true>;
        return std::make_unique<NNI>(std::move(params));
    } else {
        using NNI = ExactNearestNeighborImpl<strict, has_filter, false>;
        return std::make_unique<NNI>(std::move(params));
    }
}

template <bool has_filter>
std::unique_ptr<ExactNearestNeighborIterator>
resolve_strict(bool strict, ExactNearestNeighborIterator::Params params)
{
    if (strict) {
        return resolve_single_subspace<true, has_filter>(std::move(params));
    } else {
        return resolve_single_subspace<false, has_filter>(std::move(params));
    }
}

} // namespace <unnamed>

std::unique_ptr<ExactNearestNeighborIterator>
ExactNearestNeighborIterator::create(bool strict, fef::TermFieldMatchData &tfmd,
                                std::unique_ptr<search::tensor::DistanceCalculator> distance_calc,
                                NearestNeighborDistanceHeap &distanceHeap, const GlobalFilter &filter)
{
    Params params(tfmd, std::move(distance_calc), distanceHeap, filter);
    if (filter.is_active()) {
        return resolve_strict<true>(strict, std::move(params));
    } else  {
        return resolve_strict<false>(strict, std::move(params));
    }
}

} // namespace
