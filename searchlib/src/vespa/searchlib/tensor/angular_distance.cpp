// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "angular_distance.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <numbers>
#include <cmath>

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;
using vespalib::eval::TypedCells;

namespace search::tensor {

template<typename FloatType>
class BoundAngularDistance final : public BoundDistanceFunction {
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs;
    double _lhs_norm_sq;
public:
    explicit BoundAngularDistance(TypedCells lhs)
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator()),
          _tmpSpace(lhs.size),
          _lhs(_tmpSpace.storeLhs(lhs))
    {
        auto a = _lhs.data();
        _lhs_norm_sq = _computer.dotProduct(a, a, lhs.size);
    }
    double calc(TypedCells rhs) const noexcept override {
        size_t sz = _lhs.size();
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        auto a = _lhs.data();
        auto b = rhs_vector.data();
        double b_norm_sq = _computer.dotProduct(b, b, sz);
        double squared_norms = _lhs_norm_sq * b_norm_sq;
        double dot_product = _computer.dotProduct(a, b, sz);
        double div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double cosine_similarity = dot_product / div;
        double distance = 1.0 - cosine_similarity; // in range [0,2]
        return distance;
    }
    double convert_threshold(double threshold) const noexcept override {
        if (threshold < 0.0) {
            return 0.0;
        }
        if (threshold > std::numbers::pi) {
            return 2.0;
        }
        double cosine_similarity = cos(threshold);
        return 1.0 - cosine_similarity;
    }
    double to_rawscore(double distance) const noexcept override {
        double cosine_similarity = 1.0 - distance;
        // should be in the range [-1,1] but roundoff may cause problems:
        cosine_similarity = std::min(1.0, cosine_similarity);
        cosine_similarity = std::max(-1.0, cosine_similarity);
        double angle_distance = acos(cosine_similarity); // in range [0,pi]
        double score = 1.0 / (1.0 + angle_distance);
        return score;
    }
    double calc_with_limit(TypedCells rhs, double) const noexcept override {
        return calc(rhs);
    }
};

template class BoundAngularDistance<float>;
template class BoundAngularDistance<double>;

template <typename FloatType>
BoundDistanceFunction::UP
AngularDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundAngularDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
AngularDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    using DFT = BoundAngularDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template class AngularDistanceFunctionFactory<float>;
template class AngularDistanceFunctionFactory<double>;

}
