// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "nearest_neighbor_distance_heap.h"
#include <vespa/eval/eval/value.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/distance_function.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <cmath>

namespace search::queryeval {

class NearestNeighborIterator : public SearchIterator
{
public:
    using DenseTensorAttribute = search::tensor::DenseTensorAttribute;
    using Value = vespalib::eval::Value;

    struct Params {
        fef::TermFieldMatchData &tfmd;
        const Value &queryTensor;
        const DenseTensorAttribute &tensorAttribute;
        NearestNeighborDistanceHeap &distanceHeap;
        const search::BitVector *filter;
        const search::tensor::DistanceFunction *distanceFunction;
        
        Params(fef::TermFieldMatchData &tfmd_in,
               const Value &queryTensor_in,
               const DenseTensorAttribute &tensorAttribute_in,
               NearestNeighborDistanceHeap &distanceHeap_in,
               const search::BitVector *filter_in,
               const search::tensor::DistanceFunction *distanceFunction_in)
          : tfmd(tfmd_in),
            queryTensor(queryTensor_in),
            tensorAttribute(tensorAttribute_in),
            distanceHeap(distanceHeap_in),
            filter(filter_in),
            distanceFunction(distanceFunction_in)
        {}
    };

    NearestNeighborIterator(Params params_in)
        : _params(params_in)
    {}
    
    static std::unique_ptr<NearestNeighborIterator> create(
            bool strict,
            fef::TermFieldMatchData &tfmd,
            const Value &queryTensor,
            const search::tensor::DenseTensorAttribute &tensorAttribute,
            NearestNeighborDistanceHeap &distanceHeap,
            const search::BitVector *filter,
            const search::tensor::DistanceFunction *dist_fun);

    const Params& params() const { return _params; }
private:
    Params _params;
};

} // namespace
