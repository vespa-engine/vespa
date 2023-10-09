// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    using est_fun_t = size_t (*)(size_t lhs_size, size_t rhs_size) noexcept;

private:
    BitList _in_lhs;
    BitList _in_rhs;
    BitList _in_res;
    size_t _res_dims;
    est_fun_t _estimate;

    struct State {
        SmallVector<string_id,4>        addr_space;
        SmallVector<string_id*,4>       a_addr;
        SmallVector<const string_id*,4> overlap;
        SmallVector<string_id*,4>       b_only;
        SmallVector<size_t,4>           b_view;
        size_t                          a_subspace;
        size_t                          b_subspace;
        uint32_t                        res_dims;
        State(const bool *in_a, const bool *in_b, const bool *in_res, size_t dims);
        ~State();
    };

    static void execute_plan(const Value::Index &a, const Value::Index &b,
                             const bool *in_a, const bool *in_b, const bool *in_res,
                             size_t dims, auto &&f)
    {
        State state(in_a, in_b, in_res, dims);
        auto outer = a.create_view({});
        auto inner = b.create_view(state.b_view);
        outer->lookup({});
        while (outer->next_result(state.a_addr, state.a_subspace)) {
            inner->lookup(state.overlap);
            while (inner->next_result(state.b_only, state.b_subspace)) {
                f(state.a_subspace, state.b_subspace, ConstArrayRef<string_id>{state.addr_space.begin(), state.res_dims});
            }
        }
    }

    bool check(auto &&pred) const {
        for (size_t i = 0; i < _in_lhs.size(); ++i) {
            if (!pred(_in_lhs[i], _in_rhs[i], _in_res[i])) {
                return false;
            }
        }
        return true;
    }

    est_fun_t select_estimate() const;

public:
    SparseJoinReducePlan(const ValueType &lhs, const ValueType &rhs, const ValueType &res);
    ~SparseJoinReducePlan();
    size_t res_dims() const { return _res_dims; }
    bool is_distinct() const { return _res_dims == _in_res.size(); }
    bool maybe_forward_lhs_index() const;
    bool maybe_forward_rhs_index() const;
    size_t estimate_result_size(const Value::Index &lhs, const Value::Index &rhs) const {
        return _estimate(lhs.size(), rhs.size());
    }
    // f ~= std::function<void(size_t lhs_subspace, size_t rhs_subspace, ConstArrayRef<string_id> res_addr)>;
    void execute(const Value::Index &lhs, const Value::Index &rhs, auto &&f) const {
        if (rhs.size() < lhs.size()) {
            auto swap = [&f](auto a, auto b, auto addr) { f(b, a, addr); };
            execute_plan(rhs, lhs, _in_rhs.data(), _in_lhs.data(), _in_res.data(), _in_res.size(), swap);
        } else {
            execute_plan(lhs, rhs, _in_lhs.data(), _in_rhs.data(), _in_res.data(), _in_res.size(), f);
        }
    }
};

} // namespace
