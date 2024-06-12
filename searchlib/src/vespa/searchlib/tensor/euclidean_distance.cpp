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

template <typename VectorStoreType>
class BoundEuclideanDistance final : public BoundDistanceFunction {
private:
    using FloatType = VectorStoreType::FloatType;
    const vespalib::hwaccelrated::IAccelrated & _computer;
    mutable VectorStoreType _tmpSpace;
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

template class BoundEuclideanDistance<TemporaryVectorStore<Int8Float>>;
template class BoundEuclideanDistance<TemporaryVectorStore<float>>;
template class BoundEuclideanDistance<TemporaryVectorStore<double>>;
template class BoundEuclideanDistance<ReferenceVectorStore<Int8Float>>;
template class BoundEuclideanDistance<ReferenceVectorStore<float>>;
template class BoundEuclideanDistance<ReferenceVectorStore<double>>;

template <typename FloatType>
BoundDistanceFunction::UP
EuclideanDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundEuclideanDistance<TemporaryVectorStore<FloatType>>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
EuclideanDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        using DFT = BoundEuclideanDistance<ReferenceVectorStore<FloatType>>;
        return std::make_unique<DFT>(lhs);
    } else {
        using DFT = BoundEuclideanDistance<TemporaryVectorStore<FloatType>>;
        return std::make_unique<DFT>(lhs);
    }
}

template class EuclideanDistanceFunctionFactory<Int8Float>;
template class EuclideanDistanceFunctionFactory<float>;
template class EuclideanDistanceFunctionFactory<double>;

}
