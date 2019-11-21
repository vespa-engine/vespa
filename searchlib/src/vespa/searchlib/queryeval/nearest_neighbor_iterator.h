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

/**
 * Search iterator for K nearest neighbor matching.
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

}
