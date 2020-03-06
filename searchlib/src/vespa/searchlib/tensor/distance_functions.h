// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/tensor/dense/typed_cells.h>

namespace search::tensor {

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

/**
 * Hardware accelerated calculation of the square of the standard Euclidean distance.
 *
 * Uses vector instructions to perform calculation on smaller vectors of size VectorSizeBytes.
 * Caller must ensure that the size of the tensor vector is a multiple of VectorSizeBytes.
 */
template <typename FloatType, size_t VectorSizeBytes>
class HwAccelSquaredEuclideanDistance : public DistanceFunction {
public:
    double calc(const vespalib::tensor::TypedCells& lhs, const vespalib::tensor::TypedCells& rhs) const override {
        constexpr const size_t elems_per_vector = VectorSizeBytes / sizeof(FloatType);
        typedef FloatType VectorType __attribute__ ((vector_size (VectorSizeBytes), aligned(VectorSizeBytes)));

        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        // TODO: Handle remainder when tensor vector size is not a multiple of VectorSizeBytes.
        assert((sz % VectorSizeBytes) == 0);

        const auto* a = reinterpret_cast<const VectorType*>(lhs_vector.begin());
        const auto* b = reinterpret_cast<const VectorType*>(rhs_vector.begin());

        VectorType tmp_diff;
        VectorType tmp_squa;
        VectorType tmp_sum;
        memset(&tmp_sum, 0, sizeof(tmp_sum));

        const size_t num_ops = sz / elems_per_vector;
        for (size_t i = 0; i < num_ops; ++i) {
            tmp_diff = a[i] - b[i];
            tmp_squa = tmp_diff * tmp_diff;
            tmp_sum += tmp_squa;
        }
        double sum = 0;
        for (size_t i = 0; i < elems_per_vector; ++i) {
            sum += tmp_sum[i];
        }
        return sum;
    }
};

template class HwAccelSquaredEuclideanDistance<float, 32>;
template class HwAccelSquaredEuclideanDistance<double, 32>;

}
