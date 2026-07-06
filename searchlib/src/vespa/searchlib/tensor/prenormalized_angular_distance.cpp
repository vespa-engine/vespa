// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prenormalized_angular_distance.h"

#include "distance_function_vector_access.h"
#include "temporary_vector_store.h"

using vespalib::typify_invoke;
using vespalib::eval::Int8Float;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

template <typename VectorAccessType>
class BoundPrenormalizedAngularDistance final : public BoundDistanceFunction {
    mutable VectorAccessType _access;
    double                   _lhs_norm_sq;

public:
    template <typename... AccessArgs>
    explicit BoundPrenormalizedAngularDistance(AccessArgs&&... access_args)
        : _access(std::forward<AccessArgs>(access_args)...) {
        const auto* a = _access.lhs().data();
        _lhs_norm_sq = _access.dot_product(cast(a), cast(a)); // TODO dedicated squared L2 norm function
        if (_lhs_norm_sq <= 0.0) {
            _lhs_norm_sq = 1.0;
        }
    }
    double calc(TypedCells rhs) const noexcept override {
        auto        rhs_vector = _access.convert_rhs(rhs);
        const auto* a = _access.lhs().data();
        const auto* b = rhs_vector.data();
        double      dot_product = _access.dot_product(cast(a), cast(b));
        double      distance = _lhs_norm_sq - dot_product;
        return distance;
    }
    double convert_threshold(double threshold) const noexcept override {
        double cosine_similarity = 1.0 - threshold;
        double dot_product = cosine_similarity * _lhs_norm_sq;
        double distance = _lhs_norm_sq - dot_product;
        return distance;
    }
    double to_rawscore(double distance) const noexcept override {
        double dot_product = _lhs_norm_sq - distance;
        double cosine_similarity = dot_product / _lhs_norm_sq;
        // should be in range [-1,1] but roundoff may cause problems:
        cosine_similarity = std::min(1.0, cosine_similarity);
        cosine_similarity = std::max(-1.0, cosine_similarity);
        double cosine_distance = 1.0 - cosine_similarity; // in range [0,2]
        double score = 1.0 / (1.0 + cosine_distance);
        return score;
    }
    double calc_with_limit(TypedCells rhs, double) const noexcept override { return calc(rhs); }
};

template class BoundPrenormalizedAngularDistance<TemporaryVectorAccess<float>>;
template class BoundPrenormalizedAngularDistance<TemporaryVectorAccess<double>>;
template class BoundPrenormalizedAngularDistance<TemporaryVectorAccess<Int8Float>>;
template class BoundPrenormalizedAngularDistance<TemporaryVectorAccess<vespalib::BFloat16>>;
template class BoundPrenormalizedAngularDistance<ReferenceVectorAccess<float>>;
template class BoundPrenormalizedAngularDistance<ReferenceVectorAccess<double>>;
template class BoundPrenormalizedAngularDistance<ReferenceVectorAccess<Int8Float>>;
template class BoundPrenormalizedAngularDistance<ReferenceVectorAccess<vespalib::BFloat16>>;

template <typename FloatType>
BoundDistanceFunction::UP
PrenormalizedAngularDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundPrenormalizedAngularDistance<TemporaryVectorAccess<FloatType>>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
PrenormalizedAngularDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        using DFT = BoundPrenormalizedAngularDistance<ReferenceVectorAccess<FloatType>>;
        return std::make_unique<DFT>(lhs);
    } else {
        using DFT = BoundPrenormalizedAngularDistance<TemporaryVectorAccess<FloatType>>;
        return std::make_unique<DFT>(lhs);
    }
}

template class PrenormalizedAngularDistanceFunctionFactory<float>;
template class PrenormalizedAngularDistanceFunctionFactory<double>;
template class PrenormalizedAngularDistanceFunctionFactory<Int8Float>;
template class PrenormalizedAngularDistanceFunctionFactory<vespalib::BFloat16>;

BoundDistanceFunction::UP
QuantizedPrenormalizedAngularDistanceFunctionFactory::for_query_vector(TypedCells lhs) const {
    using DFT = BoundPrenormalizedAngularDistance<Float32LhsQuantizedRhsVectorAccess>;
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

BoundDistanceFunction::UP
QuantizedPrenormalizedAngularDistanceFunctionFactory::for_insertion_vector(TypedCells lhs) const {
    using DFT = BoundPrenormalizedAngularDistance<QuantizedLhsAndRhsVectorAccess>;
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

} // namespace search::tensor
