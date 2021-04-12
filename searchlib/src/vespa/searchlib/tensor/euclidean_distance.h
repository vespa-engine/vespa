// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    SquaredEuclideanDistance() {}
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
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
    {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        constexpr vespalib::eval::CellType expected = vespalib::eval::get_cell_type<FloatType>();
        if (__builtin_expect((lhs.type == expected && rhs.type == expected), true)) {
            auto lhs_vector = lhs.unsafe_typify<FloatType>();
            auto rhs_vector = rhs.unsafe_typify<FloatType>();
            size_t sz = lhs_vector.size();
            assert(sz == rhs_vector.size());
            return _computer.squaredEuclideanDistance(&lhs_vector[0], &rhs_vector[0], sz);
        } else {
            return SquaredEuclideanDistance::calc(lhs, rhs);
        }
    }
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
};

}
