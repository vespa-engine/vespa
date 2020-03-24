// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "nearest_neighbor_distance_heap.h"
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
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
    using DenseTensorView = vespalib::tensor::DenseTensorView;

    struct Params {
        fef::TermFieldMatchData &tfmd;
        const DenseTensorView &queryTensor;
        const DenseTensorAttribute &tensorAttribute;
        NearestNeighborDistanceHeap &distanceHeap;
        const search::tensor::DistanceFunction *distanceFunction;
        
        Params(fef::TermFieldMatchData &tfmd_in,
               const DenseTensorView &queryTensor_in,
               const DenseTensorAttribute &tensorAttribute_in,
               NearestNeighborDistanceHeap &distanceHeap_in,
               const search::tensor::DistanceFunction *distanceFunction_in)
          : tfmd(tfmd_in),
            queryTensor(queryTensor_in),
            tensorAttribute(tensorAttribute_in),
            distanceHeap(distanceHeap_in),
            distanceFunction(distanceFunction_in)
        {}
    };

    NearestNeighborIterator(Params params_in)
        : _params(params_in)
    {}
    
    static std::unique_ptr<NearestNeighborIterator> create(
            bool strict,
            fef::TermFieldMatchData &tfmd,
            const vespalib::tensor::DenseTensorView &queryTensor,
            const search::tensor::DenseTensorAttribute &tensorAttribute,
            NearestNeighborDistanceHeap &distanceHeap,
            const search::tensor::DistanceFunction *dist_fun);

    const Params& params() const { return _params; }
private:
    Params _params;
};

} // namespace
