// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prenormalized_angular_distance.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/hwaccelrated/iaccelrated.h>

using vespalib::eval::Int8Float;
using vespalib::eval::TypifyCellType;
using vespalib::typify_invoke;

namespace search::tensor {

template <typename VectorStoreType>
class BoundPrenormalizedAngularDistance final : public BoundDistanceFunction {
private:
    using FloatType = VectorStoreType::FloatType;
    const vespalib::hwaccelrated::IAccelrated & _computer;
    mutable VectorStoreType _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs;
    double _lhs_norm_sq;
public:
    explicit BoundPrenormalizedAngularDistance(TypedCells lhs)
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator()),
          _tmpSpace(lhs.size),
          _lhs(_tmpSpace.storeLhs(lhs))
    {
        auto a = _lhs.data();
        _lhs_norm_sq = _computer.dotProduct(cast(a), cast(a), lhs.size);
        if (_lhs_norm_sq <= 0.0) {
            _lhs_norm_sq = 1.0;
        }
    }
    double calc(TypedCells rhs) const noexcept override {
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        auto a = _lhs.data();
        auto b = rhs_vector.data();
        double dot_product = _computer.dotProduct(cast(a), cast(b), _lhs.size());
        double distance = _lhs_norm_sq - dot_product;
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
    double calc_with_limit(TypedCells rhs, double) const noexcept override {
        return calc(rhs);
    }
};

template class BoundPrenormalizedAngularDistance<TemporaryVectorStore<float>>;
template class BoundPrenormalizedAngularDistance<TemporaryVectorStore<double>>;
template class BoundPrenormalizedAngularDistance<TemporaryVectorStore<Int8Float>>;
template class BoundPrenormalizedAngularDistance<ReferenceVectorStore<float>>;
template class BoundPrenormalizedAngularDistance<ReferenceVectorStore<double>>;
template class BoundPrenormalizedAngularDistance<ReferenceVectorStore<Int8Float>>;

template <typename FloatType>
BoundDistanceFunction::UP
PrenormalizedAngularDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) const {
    using DFT = BoundPrenormalizedAngularDistance<TemporaryVectorStore<FloatType>>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
PrenormalizedAngularDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) const {
    if (_reference_insertion_vector) {
        using DFT = BoundPrenormalizedAngularDistance<ReferenceVectorStore<FloatType>>;
        return std::make_unique<DFT>(lhs);
    } else {
        using DFT = BoundPrenormalizedAngularDistance<TemporaryVectorStore<FloatType>>;
        return std::make_unique<DFT>(lhs);
    }
}

template class PrenormalizedAngularDistanceFunctionFactory<float>;
template class PrenormalizedAngularDistanceFunctionFactory<double>;
template class PrenormalizedAngularDistanceFunctionFactory<Int8Float>;

}
