// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hamming_distance.h"
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

double
HammingDistance::calc(const vespalib::eval::TypedCells& lhs,
                      const vespalib::eval::TypedCells& rhs) const
{
    constexpr auto expected = vespalib::eval::CellType::INT8;
    if (__builtin_expect((lhs.type == expected && rhs.type == expected), true)) {
        size_t sz = lhs.size;
        assert(sz == rhs.size);
        return (double) vespalib::binary_hamming_distance(lhs.data, rhs.data, sz);
    } else {
        return typify_invoke<2,TypifyCellType,CalcHamming>(lhs.type, rhs.type, lhs, rhs);
    }
}

double
HammingDistance::calc_with_limit(const vespalib::eval::TypedCells& lhs,
                                 const vespalib::eval::TypedCells& rhs,
                                 double) const
{
    // consider optimizing:
    return calc(lhs, rhs);
}

}
