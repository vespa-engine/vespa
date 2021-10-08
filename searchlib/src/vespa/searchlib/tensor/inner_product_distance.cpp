// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "inner_product_distance.h"

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

namespace search::tensor {

namespace {

struct CalcInnerProduct {
    template <typename LCT, typename RCT>
    static double invoke(const vespalib::eval::TypedCells& lhs,
                         const vespalib::eval::TypedCells& rhs)
    {
        auto lhs_vector = lhs.unsafe_typify<LCT>();
        auto rhs_vector = rhs.unsafe_typify<RCT>();
        size_t sz = lhs_vector.size();
        assert(sz == rhs_vector.size());
        double dot_product = 0.0;
        for (size_t i = 0; i < sz; ++i) {
            double a = lhs_vector[i];
            double b = rhs_vector[i];
            dot_product += a*b;
        }
        double score = 1.0 - dot_product; // in range [0,2]
        return std::max(0.0, score);
    }
};

}

double
InnerProductDistance::calc(const vespalib::eval::TypedCells& lhs,
                           const vespalib::eval::TypedCells& rhs) const
{
    return typify_invoke<2,TypifyCellType,CalcInnerProduct>(lhs.type, rhs.type, lhs, rhs);
}

template class InnerProductDistanceHW<float>;
template class InnerProductDistanceHW<double>;

}
