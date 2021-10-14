// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "angular_distance.h"

using vespalib::typify_invoke;
using vespalib::eval::TypifyCellType;

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

}
