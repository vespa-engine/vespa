// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates inner-product "distance" between vectors with assumed norm 1.
 * Should give same ordering as Angular distance, but is less expensive.
 */
class InnerProductDistance : public DistanceFunction {
public:
    InnerProductDistance() {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override;
    double convert_threshold(double threshold) const override {
        return threshold;
    }
    double to_rawscore(double distance) const override {
        double score = 1.0 / (1.0 + distance);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs,
                           double /*limit*/) const override
    {
        return calc(lhs, rhs);
    }
};

/**
 * Calculates inner-product "distance" between vectors with assumed norm 1.
 * Should give same ordering as Angular distance, but is less expensive.
 * Will use instruction optimal for the cpu it is running on
 * when both vectors have the expected cell type.
 */
template <typename FloatType>
class InnerProductDistanceHW : public InnerProductDistance {
public:
    InnerProductDistanceHW()
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
    {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        constexpr vespalib::eval::CellType expected = vespalib::eval::get_cell_type<FloatType>();
        if (__builtin_expect((lhs.type == expected && rhs.type == expected), true)) {
            auto lhs_vector = lhs.unsafe_typify<FloatType>();
            auto rhs_vector = rhs.unsafe_typify<FloatType>();
            size_t sz = lhs_vector.size();
            assert(sz == rhs_vector.size());
            double score = 1.0 - _computer.dotProduct(&lhs_vector[0], &rhs_vector[0], sz);
            return std::max(0.0, score);
        } else {
            return InnerProductDistance::calc(lhs, rhs);
        }
    }
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
};

}
