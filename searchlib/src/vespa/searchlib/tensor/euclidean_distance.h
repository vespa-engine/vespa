// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates the square of the standard Euclidean distance.
 */
class SquaredEuclideanDistance : public DistanceFunction {
public:
    SquaredEuclideanDistance(vespalib::eval::CellType expected) : DistanceFunction(expected) {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override;
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double limit) const override;
    double convert_threshold(double threshold) const override {
        return threshold*threshold;
    }
    double to_rawscore(double distance) const override {
        double d = sqrt(distance);
        double score = 1.0 / (1.0 + d);
        return score;
    }
};

/**
 * Calculates the square of the standard Euclidean distance.
 * Will use instruction optimal for the cpu it is running on
 * when both vectors have the expected cell type.
 */
template <typename FloatType>
class SquaredEuclideanDistanceHW : public SquaredEuclideanDistance {
public:
    SquaredEuclideanDistanceHW()
      : SquaredEuclideanDistance(vespalib::eval::get_cell_type<FloatType>()),
        _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
    {
        assert(expected_cell_type() == vespalib::eval::get_cell_type<FloatType>());
    }

    static const double *cast(const double * p) { return p; }
    static const float *cast(const float * p) { return p; }
    static const int8_t *cast(const vespalib::eval::Int8Float * p) { return reinterpret_cast<const int8_t *>(p); }
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        constexpr vespalib::eval::CellType expected = vespalib::eval::get_cell_type<FloatType>();
        assert(lhs.type == expected && rhs.type == expected);
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        return _computer.squaredEuclideanDistance(cast(&lhs_vector[0]), cast(&rhs_vector[0]), sz);
    }

    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double limit) const override
    {
        constexpr vespalib::eval::CellType expected = vespalib::eval::get_cell_type<FloatType>();
        assert(lhs.type == expected && rhs.type == expected);
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        double sum = 0.0;
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        for (size_t i = 0; i < sz && sum <= limit; ++i) {
            double diff = lhs_vector[i] - rhs_vector[i];
            sum += diff*diff;
        }
        return sum;
    }
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
};

}
