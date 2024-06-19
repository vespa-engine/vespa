// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mips_distance_transform.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <cmath>
#include <variant>

using vespalib::eval::Int8Float;

namespace search::tensor {

template <typename VectorStoreType, bool extra_dim>
class BoundMipsDistanceFunction final : public BoundDistanceFunction {
private:
    using FloatType = VectorStoreType::FloatType;
    mutable VectorStoreType _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs_vector;
    const vespalib::hwaccelerated::IAccelerated & _computer;
    double _max_sq_norm;
    using ExtraDimT = std::conditional_t<extra_dim,double,std::monostate>;
    [[no_unique_address]] ExtraDimT _lhs_extra_dim;

public:
    BoundMipsDistanceFunction(TypedCells lhs, MaximumSquaredNormStore& sq_norm_store)
        : BoundDistanceFunction(),
          _tmpSpace(lhs.size),
          _lhs_vector(_tmpSpace.storeLhs(lhs)),
          _computer(vespalib::hwaccelerated::IAccelerated::getAccelerator())
    {
        const FloatType * a = _lhs_vector.data();
        if constexpr (extra_dim) {
            double lhs_sq_norm = _computer.dotProduct(cast(a), cast(a), lhs.size);
            _max_sq_norm = sq_norm_store.get_max(lhs_sq_norm);
            _lhs_extra_dim = std::sqrt(_max_sq_norm - lhs_sq_norm);
        } else {
            _max_sq_norm = sq_norm_store.get_max();
        }
    }

    double get_extra_dim_value() requires extra_dim {
        return _lhs_extra_dim;
    }

    double calc(TypedCells rhs) const noexcept override {
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        const FloatType * a = _lhs_vector.data();
        const FloatType * b = rhs_vector.data();
        double dp = _computer.dotProduct(cast(a), cast(b), rhs.size);
        if constexpr (extra_dim) {
            double rhs_sq_norm = _computer.dotProduct(cast(b), cast(b), rhs.size);
            // avoid sqrt(negative) for robustness:
            double diff = std::max(0.0, _max_sq_norm - rhs_sq_norm);
            double rhs_extra_dim = std::sqrt(diff);
            dp += _lhs_extra_dim * rhs_extra_dim;
        }
        return -dp;
    }
    double convert_threshold(double threshold) const noexcept override {
        return threshold;
    }
    double to_rawscore(double distance) const noexcept override {
        return -distance;
    }
    double to_distance(double rawscore) const noexcept override {
        return -rawscore;
    }
    double min_rawscore() const noexcept override {
        return std::numeric_limits<double>::lowest();
    }
    double calc_with_limit(TypedCells rhs, double) const noexcept override {
        return calc(rhs);
    }
};

template<typename FloatType>
BoundDistanceFunction::UP
MipsDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    return std::make_unique<BoundMipsDistanceFunction<TemporaryVectorStore<FloatType>, false>>(lhs, *_sq_norm_store);
}

template<typename FloatType>
BoundDistanceFunction::UP
MipsDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        return std::make_unique<BoundMipsDistanceFunction<ReferenceVectorStore<FloatType>, true>>(lhs, *_sq_norm_store);
    } else {
        return std::make_unique<BoundMipsDistanceFunction<TemporaryVectorStore<FloatType>, true>>(lhs, *_sq_norm_store);
    }
};

template class MipsDistanceFunctionFactory<Int8Float>;
template class MipsDistanceFunctionFactory<float>;
template class MipsDistanceFunctionFactory<double>;

}
