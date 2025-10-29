// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "inline_operation.h"
#include <vespa/vespalib/hwaccelerated/functions.h>

namespace vespalib::eval::operation {

// These are the ironically de-inlined operations of `inline_operation.h` that may benefit
// from being in an explicit translation unit. In particular, this is for operations that
// use our own vectorized kernels, hiding the iaccelerated header details.
// TODO consider moving to header file...!

double DotProduct<Int8Float, Int8Float>::apply(const Int8Float *lhs, const Int8Float *rhs, size_t count) {
    // Do a few pre-flight checks before daring to touch the buffer as-if int8_t*...
    static_assert(sizeof(Int8Float)  == sizeof(int8_t));
    static_assert(alignof(Int8Float) == alignof(int8_t));
    static_assert(std::has_unique_object_representations_v<Int8Float>);

    const auto *lhs_as_i8 = reinterpret_cast<const int8_t*>(lhs);
    const auto *rhs_as_i8 = reinterpret_cast<const int8_t*>(rhs);
    return static_cast<double>(hwaccelerated::dot_product(lhs_as_i8, rhs_as_i8, count));
}

double DotProduct<BFloat16, BFloat16>::apply(const BFloat16 *lhs, const BFloat16 *rhs, size_t count) {
    return hwaccelerated::dot_product(lhs, rhs, count);
}

}
