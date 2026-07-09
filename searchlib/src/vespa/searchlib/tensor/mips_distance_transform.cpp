// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mips_distance_transform.h"

#include "distance_function_vector_access.h"
#include "temporary_vector_store.h"

#include <cmath>
#include <variant>

using vespalib::eval::Int8Float;

namespace search::tensor {

template <typename VectorAccessType, bool extra_dim>
class BoundMipsDistanceFunction final : public BoundDistanceFunction {
    mutable VectorAccessType _access;
    double                   _max_sq_norm;
    using ExtraDimT = std::conditional_t<extra_dim, double, std::monostate>;
    [[no_unique_address]] ExtraDimT _lhs_extra_dim;

public:
    template <typename... AccessArgs>
    BoundMipsDistanceFunction(TypedCells lhs, MaximumSquaredNormStore& sq_norm_store, AccessArgs&&... access_args)
        : BoundDistanceFunction(), _access(lhs, std::forward<AccessArgs>(access_args)...) {
        const auto* a = _access.lhs().data();
        if constexpr (extra_dim) {
            double lhs_sq_norm = _access.dot_product(cast(a), cast(a));
            _max_sq_norm = sq_norm_store.get_max(lhs_sq_norm);
            _lhs_extra_dim = std::sqrt(_max_sq_norm - lhs_sq_norm);
        } else {
            _max_sq_norm = sq_norm_store.get_max();
        }
    }

    double get_extra_dim_value()
        requires extra_dim
    {
        return _lhs_extra_dim;
    }

    double calc(TypedCells rhs) const noexcept override {
        auto        rhs_vector = _access.convert_rhs(rhs);
        const auto* a = _access.lhs().data();
        const auto* b = rhs_vector.data();
        double      dp = _access.dot_product(cast(a), cast(b));
        if constexpr (extra_dim) {
            double rhs_sq_norm = _access.dot_product(cast(b), cast(b));
            // avoid sqrt(negative) for robustness:
            double diff = std::max(0.0, _max_sq_norm - rhs_sq_norm);
            double rhs_extra_dim = std::sqrt(diff);
            dp += _lhs_extra_dim * rhs_extra_dim;
        }
        return -dp;
    }
    double convert_threshold(double threshold) const noexcept override { return threshold; }
    double to_rawscore(double distance) const noexcept override { return -distance; }
    double to_distance(double rawscore) const noexcept override { return -rawscore; }
    double min_rawscore() const noexcept override { return std::numeric_limits<double>::lowest(); }
    double calc_with_limit(TypedCells rhs, double) const noexcept override { return calc(rhs); }
};

template <typename FloatType>
BoundDistanceFunction::UP MipsDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    return std::make_unique<BoundMipsDistanceFunction<TemporaryVectorAccess<FloatType>, false>>(lhs, *_sq_norm_store);
}

template <typename FloatType>
BoundDistanceFunction::UP MipsDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        return std::make_unique<BoundMipsDistanceFunction<ReferenceVectorAccess<FloatType>, true>>(lhs,
                                                                                                   *_sq_norm_store);
    } else {
        return std::make_unique<BoundMipsDistanceFunction<TemporaryVectorAccess<FloatType>, true>>(lhs,
                                                                                                   *_sq_norm_store);
    }
};

template class MipsDistanceFunctionFactory<Int8Float>;
template class MipsDistanceFunctionFactory<vespalib::BFloat16>;
template class MipsDistanceFunctionFactory<float>;
template class MipsDistanceFunctionFactory<double>;

BoundDistanceFunction::UP QuantizedMipsDistanceFunctionFactory::for_query_vector(TypedCells lhs) const {
    return std::make_unique<BoundMipsDistanceFunction<Float32LhsQuantizedRhsVectorAccess, false>>(
        lhs, *_sq_norm_store, _dimensions, _bits, _seed);
}

BoundDistanceFunction::UP QuantizedMipsDistanceFunctionFactory::for_insertion_vector(TypedCells lhs) const {
    return std::make_unique<BoundMipsDistanceFunction<QuantizedLhsAndRhsVectorAccess, true>>(
        lhs, *_sq_norm_store, _dimensions, _bits, _seed);
};

} // namespace search::tensor
