// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/nested_loop.h>
#include <vespa/vespalib/util/small_vector.h>

namespace vespalib::eval::instruction {

struct DenseJoinReducePlan {
    size_t lhs_size;
    size_t rhs_size;
    size_t res_size;
    SmallVector<size_t> loop_cnt;
    SmallVector<size_t> lhs_stride;
    SmallVector<size_t> rhs_stride;
    SmallVector<size_t> res_stride;
    DenseJoinReducePlan(const ValueType &lhs, const ValueType &rhs, const ValueType &res);
    ~DenseJoinReducePlan();
    template <typename F> void execute(size_t lhs, size_t rhs, size_t res, const F &f) const {
        run_nested_loop(lhs, rhs, res, loop_cnt, lhs_stride, rhs_stride, res_stride, f);
    }
    bool distinct_result() const;
};

} // namespace
