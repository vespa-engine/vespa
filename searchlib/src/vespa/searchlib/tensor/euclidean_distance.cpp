// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "euclidean_distance.h"
#include "temporary_vector_store.h"

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

namespace {

struct CalcEuclidean {
    template <typename LCT, typename RCT>
    static double invoke(const vespalib::eval::TypedCells& lhs,
                         const vespalib::eval::TypedCells& rhs)
    {
        auto lhs_vector = lhs.unsafe_typify<LCT>();
        auto rhs_vector = rhs.unsafe_typify<RCT>();
        double sum = 0.0;
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        for (size_t i = 0; i < sz; ++i) {
            double diff = lhs_vector[i] - rhs_vector[i];
            sum += diff*diff;
        }
        return sum;
    }
};

}

using vespalib::eval::Int8Float;
using vespalib::BFloat16;

template<typename AttributeCellType>
class BoundEuclideanDistance : public BoundDistanceFunction {
    using FloatType = std::conditional_t<std::is_same<AttributeCellType,BFloat16>::value,float,AttributeCellType>;
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs_vector;
    static const double *cast(const double * p) { return p; }
    static const float *cast(const float * p) { return p; }
    static const int8_t *cast(const Int8Float * p) { return reinterpret_cast<const int8_t *>(p); }
public:
    BoundEuclideanDistance(const vespalib::eval::TypedCells& lhs)
        : _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator()),
          _tmpSpace(lhs.size),
          _lhs_vector(_tmpSpace.storeLhs(lhs))
    {}
    double calc(const vespalib::eval::TypedCells& rhs) const override {
        size_t sz = _lhs_vector.size();
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        assert(sz == rhs_vector.size());
        auto a = _lhs_vector.data();
        auto b = rhs_vector.data();
        return _computer.squaredEuclideanDistance(cast(a), cast(b), sz);
    }
    double convert_threshold(double threshold) const override {
        return threshold*threshold;
    }
    double to_rawscore(double distance) const override {
        double d = sqrt(distance);
        double score = 1.0 / (1.0 + d);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& rhs, double limit) const override {
        vespalib::ConstArrayRef<AttributeCellType> rhs_vector = rhs.typify<AttributeCellType>();
        double sum = 0.0;
        size_t sz = _lhs_vector.size();
        assert(sz == rhs_vector.size());
        for (size_t i = 0; i < sz && sum <= limit; ++i) {
            double diff = _lhs_vector[i] - rhs_vector[i];
            sum += diff*diff;
        }
        return sum;
    }
};

template class BoundEuclideanDistance<Int8Float>;
template class BoundEuclideanDistance<BFloat16>;
template class BoundEuclideanDistance<float>;
template class BoundEuclideanDistance<double>;

template <typename FloatType>
BoundDistanceFunction::UP
EuclideanDistanceFunctionFactory<FloatType>::for_query_vector(const vespalib::eval::TypedCells& lhs) {
    using DFT = BoundEuclideanDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
EuclideanDistanceFunctionFactory<FloatType>::for_insertion_vector(const vespalib::eval::TypedCells& lhs) {
    using DFT = BoundEuclideanDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template class EuclideanDistanceFunctionFactory<Int8Float>;
template class EuclideanDistanceFunctionFactory<BFloat16>;
template class EuclideanDistanceFunctionFactory<float>;
template class EuclideanDistanceFunctionFactory<double>;

}
