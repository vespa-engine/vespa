// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mips_distance_transform.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <cmath>
#include <mutex>
#include <variant>

using vespalib::eval::Int8Float;

namespace search::tensor {

template<typename FloatType, bool extra_dim>
class BoundMipsDistanceFunction : public BoundDistanceFunction {
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs_vector;
    const vespalib::hwaccelrated::IAccelrated & _computer;
    double _max_sq_norm;
    using ExtraDimT = std::conditional_t<extra_dim,double,std::monostate>;
    [[no_unique_address]] ExtraDimT _lhs_extra_dim;

    static const double *cast(const double * p) { return p; }
    static const float *cast(const float * p) { return p; }
    static const int8_t *cast(const Int8Float * p) { return reinterpret_cast<const int8_t *>(p); }
public:
    BoundMipsDistanceFunction(const vespalib::eval::TypedCells& lhs, MaximumSquaredNormStore& sq_norm_store)
        : BoundDistanceFunction(),
          _tmpSpace(lhs.size),
          _lhs_vector(_tmpSpace.storeLhs(lhs)),
          _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator())
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

    double calc(const vespalib::eval::TypedCells &rhs) const override {
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
    double convert_threshold(double threshold) const override {
        return threshold;
    }
    double to_rawscore(double distance) const override {
        return -distance;
    }
    double to_distance(double rawscore) const override {
        return -rawscore;
    }
    double min_rawscore() const override {
        return std::numeric_limits<double>::lowest();
    }
    double calc_with_limit(const vespalib::eval::TypedCells& rhs, double) const override {
        return calc(rhs);
    }
};

template<typename FloatType>
BoundDistanceFunction::UP
MipsDistanceFunctionFactory<FloatType>::for_query_vector(const vespalib::eval::TypedCells& lhs) {
    return std::make_unique<BoundMipsDistanceFunction<FloatType, false>>(lhs, *_sq_norm_store);
}

template<typename FloatType>
BoundDistanceFunction::UP
MipsDistanceFunctionFactory<FloatType>::for_insertion_vector(const vespalib::eval::TypedCells& lhs) {
    return std::make_unique<BoundMipsDistanceFunction<FloatType, true>>(lhs, *_sq_norm_store);
};

template class MipsDistanceFunctionFactory<Int8Float>;
template class MipsDistanceFunctionFactory<float>;
template class MipsDistanceFunctionFactory<double>;

}
