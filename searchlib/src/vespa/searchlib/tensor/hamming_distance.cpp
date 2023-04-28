// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hamming_distance.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/util/binary_hamming_distance.h>

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

namespace {

struct CalcHamming {
    template <typename LCT, typename RCT>
    static double invoke(const vespalib::eval::TypedCells& lhs,
                         const vespalib::eval::TypedCells& rhs)
    {
        auto lhs_vector = lhs.unsafe_typify<LCT>();
        auto rhs_vector = rhs.unsafe_typify<RCT>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        size_t sum = 0;
        for (size_t i = 0; i < sz; ++i) {
            sum += (lhs_vector[i] == rhs_vector[i]) ? 0 : 1;
        }
        return (double)sum;
    }
};

}

using vespalib::eval::Int8Float;

template<typename FloatType>
class BoundHammingDistance : public BoundDistanceFunction {
private:
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs_vector;
public:
    BoundHammingDistance(const vespalib::eval::TypedCells& lhs)
        : _tmpSpace(lhs.size),
          _lhs_vector(_tmpSpace.storeLhs(lhs))
    {}
    double calc(const vespalib::eval::TypedCells& rhs) const override {
        size_t sz = _lhs_vector.size();
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        assert(sz == rhs_vector.size());
        auto a = _lhs_vector.data();
        auto b = rhs_vector.data();
        if constexpr (std::is_same<Int8Float, FloatType>::value) {
            return (double) vespalib::binary_hamming_distance(a, b, sz);
        } else {
            size_t sum = 0;
            for (size_t i = 0; i < sz; ++i) {
                sum += (_lhs_vector[i] == rhs_vector[i]) ? 0 : 1;
            }
            return (double)sum;
        }
    }
    double convert_threshold(double threshold) const override {
        return threshold;
    }
    double to_rawscore(double distance) const override {
        double score = 1.0 / (1.0 + distance);
        return score;
    }
    double calc_with_limit(const vespalib::eval::TypedCells& rhs, double) const override {
        // consider optimizing:
        return calc(rhs);
    }
};

template <typename FloatType>
BoundDistanceFunction::UP
HammingDistanceFunctionFactory<FloatType>::for_query_vector(const vespalib::eval::TypedCells& lhs) {
    using DFT = BoundHammingDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
HammingDistanceFunctionFactory<FloatType>::for_insertion_vector(const vespalib::eval::TypedCells& lhs) {
    using DFT = BoundHammingDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template class HammingDistanceFunctionFactory<Int8Float>;
template class HammingDistanceFunctionFactory<float>;
template class HammingDistanceFunctionFactory<double>;

}
