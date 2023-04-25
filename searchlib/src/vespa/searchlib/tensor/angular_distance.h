// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "bound_distance_function.h"
#include "distance_function_factory.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <cmath>

namespace search::tensor {

/**
 * Calculates angular distance between vectors
 */
class AngularDistance : public DistanceFunction {
public:
    AngularDistance(vespalib::eval::CellType expected) : DistanceFunction(expected) {}
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override;
    double convert_threshold(double threshold) const override {
        double cosine_similarity = cos(threshold);
        return 1.0 - cosine_similarity;
    }
    double to_rawscore(double distance) const override {
        double cosine_similarity = 1.0 - distance;
        // should be in the range [-1,1] but roundoff may cause problems:
        cosine_similarity = std::min(1.0, cosine_similarity);
        cosine_similarity = std::max(-1.0, cosine_similarity);
        double angle_distance = acos(cosine_similarity); // in range [0,pi]
        double score = 1.0 / (1.0 + angle_distance);
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
 * Calculates angular distance between vectors
 * Will use instruction optimal for the cpu it is running on
 * when both vectors have the expected cell type.
 */
template <typename FloatType>
class AngularDistanceHW : public AngularDistance {
public:
    AngularDistanceHW()
      : AngularDistance(vespalib::eval::get_cell_type<FloatType>()),
        _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
    {
        assert(expected_cell_type() == vespalib::eval::get_cell_type<FloatType>());
    }
    double calc(const vespalib::eval::TypedCells& lhs, const vespalib::eval::TypedCells& rhs) const override {
        constexpr vespalib::eval::CellType expected = vespalib::eval::get_cell_type<FloatType>();
        assert(lhs.type == expected && rhs.type == expected);
        auto lhs_vector = lhs.typify<FloatType>();
        auto rhs_vector = rhs.typify<FloatType>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        auto a = lhs_vector.data();
        auto b = rhs_vector.data();
        double a_norm_sq = _computer.dotProduct(a, a, sz);
        double b_norm_sq = _computer.dotProduct(b, b, sz);
        double squared_norms = a_norm_sq * b_norm_sq;
        double dot_product = _computer.dotProduct(a, b, sz);
        double div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double cosine_similarity = dot_product / div;
        double distance = 1.0 - cosine_similarity; // in range [0,2]
        return distance;
    }
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
};

template <typename FloatType>
class AngularDistanceFunctionFactory : public DistanceFunctionFactory {
public:
    AngularDistanceFunctionFactory()
        : DistanceFunctionFactory(vespalib::eval::get_cell_type<FloatType>())
        {}

    BoundDistanceFunction::UP for_query_vector(const vespalib::eval::TypedCells& lhs) override;
    BoundDistanceFunction::UP for_insertion_vector(const vespalib::eval::TypedCells& lhs) override;
};

}
