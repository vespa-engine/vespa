// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "euclidean_distance.h"

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

double
SquaredEuclideanDistance::calc(const vespalib::eval::TypedCells& lhs,
                               const vespalib::eval::TypedCells& rhs) const
{
    return typify_invoke<2,TypifyCellType,CalcEuclidean>(lhs.type, rhs.type, lhs, rhs);
}

double
SquaredEuclideanDistance::calc_with_limit(const vespalib::eval::TypedCells& lhs,
                                          const vespalib::eval::TypedCells& rhs,
                                          double) const
{
    // maybe optimize this:
    return typify_invoke<2,TypifyCellType,CalcEuclidean>(lhs.type, rhs.type, lhs, rhs);
}

template class SquaredEuclideanDistanceHW<float>;
template class SquaredEuclideanDistanceHW<double>;

}
