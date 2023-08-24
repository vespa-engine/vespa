// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/small_vector.h>
#include <functional>

namespace vespalib::eval::instruction {

class SparseJoinReducePlan
{
public:
    friend class SparseJoinReducePlanTest;

    using BitList = SmallVector<bool,8>;
    using est_fun_t = std::function<size_t(size_t lhs_size, size_t rhs_size)>;
    using F = std::function<void(size_t lhs_subspace, size_t rhs_subspace, ConstArrayRef<string_id> res_addr)>;

private:
    BitList _in_lhs;
    BitList _in_rhs;
    BitList _in_res;
    est_fun_t _estimate;

public:
    SparseJoinReducePlan(const ValueType &lhs, const ValueType &rhs, const ValueType &res);
    ~SparseJoinReducePlan();
    size_t estimate_result_size(const Value::Index &lhs, const Value::Index &rhs) const {
        return _estimate(lhs.size(), rhs.size());
    }
    void execute(const Value::Index &lhs, const Value::Index &rhs, F f) const;
};

} // namespace
