// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

namespace search::tensor {

/**
 * Calculates the square of the standard Euclidean distance.
 */
template <typename FloatType>
class SquaredEuclideanDistance : public DistanceFunction {
public:
    SquaredEuclideanDistance()
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelrator())
    {}
    double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        return _computer->squaredEuclidianDistance(&lhs_vector[0], &rhs_vector[0], sz);
    }
    vespalib::hwaccelrated::IAccelrated::UP _computer;
};

template class SquaredEuclideanDistance<float>;
template class SquaredEuclideanDistance<double>;

/**
 * Hardware accelerated calculation of the square of the standard Euclidean distance.
 *
 * Uses vector instructions to perform calculation on smaller vectors of size VectorSizeBytes.
 * Caller must ensure that the size of the tensor vector is a multiple of VectorSizeBytes.
 */
template <typename FloatType, size_t VectorSizeBytes>
class HwAccelSquaredEuclideanDistance : public DistanceFunction {
public:
    HwAccelSquaredEuclideanDistance()
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelrator())
    {}
    double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();

        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        return _computer->squaredEuclidianDistance(&lhs_vector[0], &rhs_vector[0], sz);
    }
    vespalib::hwaccelrated::IAccelrated::UP _computer;
};

template class HwAccelSquaredEuclideanDistance<float, 32>;
template class HwAccelSquaredEuclideanDistance<double, 32>;

}
