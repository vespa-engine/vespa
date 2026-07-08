// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "angular_distance.h"

#include "distance_function_vector_access.h"
#include "temporary_vector_store.h"

#include <cmath>
#include <numbers>

using vespalib::typify_invoke;
using vespalib::eval::CellType;
using vespalib::eval::Int8Float;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

template <typename VectorAccessType>
class BoundAngularDistance final : public BoundDistanceFunction {
    mutable VectorAccessType _access;
    double                   _lhs_norm_sq;

public:
    template <typename... AccessArgs>
    explicit BoundAngularDistance(AccessArgs&&... access_args) : _access(std::forward<AccessArgs>(access_args)...) {
        const auto* a = _access.lhs().data();
        _lhs_norm_sq = _access.dot_product(cast(a), cast(a)); // TODO dedicated squared L2 norm function
    }
    ~BoundAngularDistance() override;

    double calc(TypedCells rhs) const noexcept override {
        auto        rhs_vector = _access.convert_rhs(rhs);
        const auto* a = _access.lhs().data();
        const auto* b = rhs_vector.data();
        double      b_norm_sq = _access.dot_product(cast(b), cast(b));
        double      squared_norms = _lhs_norm_sq * b_norm_sq;
        double      dot_product = _access.dot_product(cast(a), cast(b));
        double      div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double      cosine_similarity = dot_product / div;
        double      distance = 1.0 - cosine_similarity; // in range [0,2]
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
    double calc_with_limit(TypedCells rhs, double) const noexcept override { return calc(rhs); }
};

template <typename VectorAccessType>
BoundAngularDistance<VectorAccessType>::~BoundAngularDistance() = default;

template class BoundAngularDistance<TemporaryVectorAccess<float>>;
template class BoundAngularDistance<TemporaryVectorAccess<double>>;
template class BoundAngularDistance<TemporaryVectorAccess<Int8Float>>;
template class BoundAngularDistance<TemporaryVectorAccess<vespalib::BFloat16>>;
template class BoundAngularDistance<ReferenceVectorAccess<float>>;
template class BoundAngularDistance<ReferenceVectorAccess<double>>;
template class BoundAngularDistance<ReferenceVectorAccess<Int8Float>>;
template class BoundAngularDistance<ReferenceVectorAccess<vespalib::BFloat16>>;

template <typename FloatType>
BoundDistanceFunction::UP AngularDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundAngularDistance<TemporaryVectorAccess<FloatType>>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP AngularDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        using DFT = BoundAngularDistance<ReferenceVectorAccess<FloatType>>;
        return std::make_unique<DFT>(lhs);
    } else {
        using DFT = BoundAngularDistance<TemporaryVectorAccess<FloatType>>;
        return std::make_unique<DFT>(lhs);
    }
}

BoundDistanceFunction::UP QuantizedAngularDistanceFunctionFactory::for_query_vector(TypedCells lhs) const {
    using DFT = BoundAngularDistance<Float32LhsQuantizedRhsVectorAccess>;
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

BoundDistanceFunction::UP QuantizedAngularDistanceFunctionFactory::for_insertion_vector(TypedCells lhs) const {
    using DFT = BoundAngularDistance<QuantizedLhsAndRhsVectorAccess>;
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

template class AngularDistanceFunctionFactory<float>;
template class AngularDistanceFunctionFactory<double>;
template class AngularDistanceFunctionFactory<Int8Float>;
template class AngularDistanceFunctionFactory<vespalib::BFloat16>;

} // namespace search::tensor
