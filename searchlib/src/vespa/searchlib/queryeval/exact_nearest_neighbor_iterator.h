// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "nearest_neighbor_distance_heap.h"
#include <vespa/eval/eval/value.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <cmath>

namespace search::tensor { class DistanceCalculator; }

namespace search::queryeval {

class GlobalFilter;

class ExactNearestNeighborIterator : public SearchIterator
{
public:
    using ITensorAttribute = search::tensor::ITensorAttribute;
    using Value = vespalib::eval::Value;

    struct Params {
        fef::TermFieldMatchData &tfmd;
        std::unique_ptr<search::tensor::DistanceCalculator> distance_calc;
        NearestNeighborDistanceHeap &distanceHeap;
        const GlobalFilter &filter;

        Params(fef::TermFieldMatchData &tfmd_in,
               std::unique_ptr<search::tensor::DistanceCalculator> distance_calc_in,
               NearestNeighborDistanceHeap &distanceHeap_in,
               const GlobalFilter &filter_in);
        Params(Params&& rhs);
        ~Params();
    };

    explicit ExactNearestNeighborIterator(Params params_in)
        : _params(std::move(params_in))
    {}

    static std::unique_ptr<ExactNearestNeighborIterator> create(
            bool strict,
            fef::TermFieldMatchData &tfmd,
            std::unique_ptr<search::tensor::DistanceCalculator> distance_calc,
            NearestNeighborDistanceHeap &distanceHeap,
            const GlobalFilter &filter,
            bool readonly_distance_heap);

    const Params& params() const { return _params; }
private:
    Params _params;
};

} // namespace
