// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"

namespace search::tensor {

// TODO: Make distance functions hardware optimized.

/**
 * Calculates the square of the standard Euclidean distance.
 */
template <typename FloatType>
class SquaredEuclideanDistance : public DistanceFunction {
public:
    double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const override {
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        double result = 0.0;
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        for (size_t i = 0; i < sz; ++i) {
            double diff = lhs_vector[i] - rhs_vector[i];
            result += diff * diff;
        }
        return result;
    }
};

template class SquaredEuclideanDistance<float>;
template class SquaredEuclideanDistance<double>;

}
