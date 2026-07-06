// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "euclidean_distance.h"

#include "distance_function_vector_access.h"
#include "temporary_vector_store.h"

#include <cmath>

using vespalib::typify_invoke;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

using vespalib::eval::Int8Float;

template <typename VectorAccessType>
class BoundEuclideanDistance final : public BoundDistanceFunction {
    mutable VectorAccessType _access;

public:
    template <typename... AccessArgs>
    explicit BoundEuclideanDistance(AccessArgs&&... access_args)
        : _access(std::forward<AccessArgs>(access_args)...) {}

    double calc(TypedCells rhs) const noexcept override {
        auto        rhs_vector = _access.convert_rhs(rhs);
        const auto* a = _access.lhs().data();
        const auto* b = rhs_vector.data();
        return _access.squared_euclidean_distance(cast(a), cast(b));
    }
    double convert_threshold(double threshold) const noexcept override { return threshold * threshold; }
    double to_rawscore(double distance) const noexcept override {
        double d = sqrt(distance);
        double score = 1.0 / (1.0 + d);
        return score;
    }
    double calc_with_limit(TypedCells rhs, double) const noexcept override { return calc(rhs); }
};

template class BoundEuclideanDistance<TemporaryVectorAccess<Int8Float>>;
template class BoundEuclideanDistance<TemporaryVectorAccess<vespalib::BFloat16>>;
template class BoundEuclideanDistance<TemporaryVectorAccess<float>>;
template class BoundEuclideanDistance<TemporaryVectorAccess<double>>;
template class BoundEuclideanDistance<ReferenceVectorAccess<Int8Float>>;
template class BoundEuclideanDistance<ReferenceVectorAccess<vespalib::BFloat16>>;
template class BoundEuclideanDistance<ReferenceVectorAccess<float>>;
template class BoundEuclideanDistance<ReferenceVectorAccess<double>>;

template <typename FloatType>
BoundDistanceFunction::UP EuclideanDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundEuclideanDistance<TemporaryVectorAccess<FloatType>>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP EuclideanDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        using DFT = BoundEuclideanDistance<ReferenceVectorAccess<FloatType>>;
        return std::make_unique<DFT>(lhs);
    } else {
        using DFT = BoundEuclideanDistance<TemporaryVectorAccess<FloatType>>;
        return std::make_unique<DFT>(lhs);
    }
}

template class EuclideanDistanceFunctionFactory<Int8Float>;
template class EuclideanDistanceFunctionFactory<vespalib::BFloat16>;
template class EuclideanDistanceFunctionFactory<float>;
template class EuclideanDistanceFunctionFactory<double>;

BoundDistanceFunction::UP QuantizedEuclideanDistanceFunctionFactory::for_query_vector(TypedCells lhs) const {
    using DFT = BoundEuclideanDistance<Float32LhsQuantizedRhsVectorAccess>;
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

BoundDistanceFunction::UP QuantizedEuclideanDistanceFunctionFactory::for_insertion_vector(TypedCells lhs) const {
    using DFT = BoundEuclideanDistance<QuantizedLhsAndRhsVectorAccess>;
    return std::make_unique<DFT>(lhs, _dimensions, _bits, _seed);
}

} // namespace search::tensor
