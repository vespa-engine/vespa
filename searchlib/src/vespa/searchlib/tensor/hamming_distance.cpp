// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hamming_distance.h"
#include "temporary_vector_store.h"
#include <vespa/vespalib/util/binary_hamming_distance.h>

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;
using vespalib::eval::TypedCells;

namespace search::tensor {

using vespalib::eval::Int8Float;

template<typename FloatType>
class BoundHammingDistance final : public BoundDistanceFunction {
private:
    mutable TemporaryVectorStore<FloatType> _tmpSpace;
    const vespalib::ConstArrayRef<FloatType> _lhs_vector;
public:
    explicit BoundHammingDistance(TypedCells lhs)
        : _tmpSpace(lhs.size),
          _lhs_vector(_tmpSpace.storeLhs(lhs))
    {}
    double calc(TypedCells rhs) const noexcept override {
        size_t sz = _lhs_vector.size();
        vespalib::ConstArrayRef<FloatType> rhs_vector = _tmpSpace.convertRhs(rhs);
        if constexpr (std::is_same<Int8Float, FloatType>::value) {
            return (double) vespalib::binary_hamming_distance(_lhs_vector.data(), rhs_vector.data(), sz);
        } else {
            assert(sz == rhs_vector.size());
            size_t sum = 0;
            for (size_t i = 0; i < sz; ++i) {
                sum += (_lhs_vector[i] == rhs_vector[i]) ? 0 : 1;
            }
            return (double)sum;
        }
    }
    double convert_threshold(double threshold) const noexcept override {
        return threshold;
    }
    double to_rawscore(double distance) const noexcept override {
        return 1.0 / (1.0 + distance);
    }
    double calc_with_limit(TypedCells rhs, double) const noexcept override {
        // consider optimizing:
        return calc(rhs);
    }
};

template <typename FloatType>
BoundDistanceFunction::UP
HammingDistanceFunctionFactory<FloatType>::for_query_vector(TypedCells lhs) {
    using DFT = BoundHammingDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template <typename FloatType>
BoundDistanceFunction::UP
HammingDistanceFunctionFactory<FloatType>::for_insertion_vector(TypedCells lhs) {
    using DFT = BoundHammingDistance<FloatType>;
    return std::make_unique<DFT>(lhs);
}

template class HammingDistanceFunctionFactory<Int8Float>;
template class HammingDistanceFunctionFactory<float>;
template class HammingDistanceFunctionFactory<double>;

}
