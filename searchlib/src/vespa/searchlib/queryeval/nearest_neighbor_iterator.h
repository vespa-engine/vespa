// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "nearest_neighbor_distance_heap.h"
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <cmath>

namespace search::queryeval {

class NearestNeighborIteratorFactory
{
public:
    static std::unique_ptr<SearchIterator> createIterator(
        bool strict,
        fef::TermFieldMatchData &tfmd,
        const vespalib::tensor::DenseTensorView &queryTensor,
        const search::tensor::DenseTensorAttribute &tensorAttribute,
        NearestNeighborDistanceHeap &distanceHeap);
};

}
