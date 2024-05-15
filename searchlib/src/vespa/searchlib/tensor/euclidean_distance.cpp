// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "euclidean_distance.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>
#include <cmath>

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;
using vespalib::eval::TypedCells;

namespace search::tensor {

using vespalib::eval::Int8Float;
using vespalib::BFloat16;

template<typename AttributeCellType>
class BoundEuclideanDistance final : public BoundDistanceFunction {
    using FloatType = std::conditional_t<std::is_same<AttributeCellType, BFloat16>::value, float, AttributeCellType>;
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs_vector;
public:
    explicit BoundEuclideanDistance(TypedCells lhs)
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator()),
          _tmpSpace(lhs.size),
          _lhs_vector(_tmpSpace.storeLhs(lhs))
    {}
    double calc(TypedCells rhs) const noexcept override {
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        auto a = _lhs_vector.data();
        auto b = rhs_vector.data();
        return _computer.squaredEuclideanDistance(cast(a), cast(b), _lhs_vector.size());
    }
    double convert_threshold(double threshold) const noexcept override {
        return threshold*threshold;
    }
    double to_rawscore(double distance) const noexcept override {
        double d = sqrt(distance);
        double score = 1.0 / (1.0 + d);
        return score;
    }
    double calc_with_limit(TypedCells rhs, double) const noexcept override {
        return calc(rhs);
    }
};

template class BoundEuclideanDistance<Int8Float>;
template class BoundEuclideanDistance<BFloat16>;
template class BoundEuclideanDistance<float>;
template class BoundEuclideanDistance<double>;

template <typename FloatType>
BoundDistanceFunction::UP
EuclideanDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundEuclideanDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
EuclideanDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    using DFT = BoundEuclideanDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template class EuclideanDistanceFunctionFactory<Int8Float>;
template class EuclideanDistanceFunctionFactory<BFloat16>;
template class EuclideanDistanceFunctionFactory<float>;
template class EuclideanDistanceFunctionFactory<double>;

}
