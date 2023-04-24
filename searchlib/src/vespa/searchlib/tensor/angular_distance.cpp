// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "angular_distance.h"
#include "temporary_vector_store.h"

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;
using vespalib::eval::TypedCells;

namespace search::tensor {

namespace {

struct CalcAngular {
    template <typename LCT, typename RCT>
    static double invoke(const vespalib::eval::TypedCells& lhs,
                         const vespalib::eval::TypedCells& rhs)
    {
        auto lhs_vector = lhs.unsafe_typify<LCT>();
        auto rhs_vector = rhs.unsafe_typify<RCT>();

        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        double a_norm_sq = 0.0;
        double b_norm_sq = 0.0;
        double dot_product = 0.0;
        for (size_t i = 0; i < sz; ++i) {
            double a = lhs_vector[i];
            double b = rhs_vector[i];
            a_norm_sq += a*a;
            b_norm_sq += b*b;
            dot_product += a*b;
        }
        double squared_norms = a_norm_sq * b_norm_sq;
        double div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double cosine_similarity = dot_product / div;
        double distance = 1.0 - cosine_similarity; // in range [0,2]
        return std::max(0.0, distance);
    }
};

}

double
AngularDistance::calc(const vespalib::eval::TypedCells& lhs,
                      const vespalib::eval::TypedCells& rhs) const
{
    return typify_invoke<2,TypifyCellType,CalcAngular>(lhs.type, rhs.type, lhs, rhs);
}

template class AngularDistanceHW<float>;
template class AngularDistanceHW<double>;


template<typename FloatType>
class BoundAngularDistance : public BoundDistanceFunction {
private:
    const vespalib::hwaccelrated::IAccelrated & _computer;
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs;
    double _lhs_norm_sq;
public:
    BoundAngularDistance(const vespalib::eval::TypedCells& lhs)
        : BoundDistanceFunction(vespalib::eval::get_cell_type<FloatType>()),
          _computer(vespalib::hwaccelrated::IAccelrated::getAccelerator()),
          _tmpSpace(lhs.size),
          _lhs(_tmpSpace.storeLhs(lhs))
    {
        auto a = _lhs.data();
        _lhs_norm_sq = _computer.dotProduct(a, a, lhs.size);
    }
    double calc(const vespalib::eval::TypedCells& rhs) const override {
        size_t sz = _lhs.size();
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        assert(sz == rhs_vector.size());
        auto a = _lhs.data();
        auto b = rhs_vector.data();
        double b_norm_sq = _computer.dotProduct(b, b, sz);
        double squared_norms = _lhs_norm_sq * b_norm_sq;
        double dot_product = _computer.dotProduct(a, b, sz);
        double div = (squared_norms > 0) ? sqrt(squared_norms) : 1.0;
        double cosine_similarity = dot_product / div;
        double distance = 1.0 - cosine_similarity; // in range [0,2]
        return distance;
    }
    double convert_threshold(double threshold) const override {
        double cosine_similarity = cos(threshold);
        return 1.0 - cosine_similarity;
    }
    double to_rawscore(double distance) const override {
        double cosine_similarity = 1.0 - distance;
        // should be in the range [-1,1] but roundoff may cause problems:
        cosine_similarity = std::min(1.0, cosine_similarity);
        cosine_similarity = std::max(-1.0, cosine_similarity);
        double angle_distance = acos(cosine_similarity); // in range [0,pi]
        double score = 1.0 / (1.0 + angle_distance);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& rhs, double) const override {
        return calc(rhs);
    }
};

template class BoundAngularDistance<float>;
template class BoundAngularDistance<double>;

template <typename FloatType>
BoundDistanceFunction::UP
AngularDistanceFunctionFactory<FloatType>::for_query_vector(const vespalib::eval::TypedCells& lhs) {
    using DFT = BoundAngularDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
AngularDistanceFunctionFactory<FloatType>::for_insertion_vector(const vespalib::eval::TypedCells& lhs) {
    using DFT = BoundAngularDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template class AngularDistanceFunctionFactory<float>;
template class AngularDistanceFunctionFactory<double>;

}
