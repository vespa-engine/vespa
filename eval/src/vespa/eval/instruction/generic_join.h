// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/interpreted_function.h>

namespace vespalib { class Stash; }
namespace vespalib::eval { class ValueBuilderFactory; }

namespace vespalib::eval::instruction {

using join_fun_t = double (*)(double, double);

//-----------------------------------------------------------------------------

struct GenericJoin {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &lhs_type, const ValueType &rhs_type, join_fun_t function,
                     const ValueBuilderFactory &factory, Stash &stash);
};

//-----------------------------------------------------------------------------

/**
 * Plan for how to traverse two partially overlapping dense subspaces
 * in parallel, identifying all matching cell index combinations, in
 * the exact order the joined cells will be stored in the result. The
 * plan can be made up-front during tensor function compilation.
 **/
struct DenseJoinPlan {
    size_t lhs_size;
    size_t rhs_size;
    size_t out_size;
    std::vector<size_t> loop_cnt;
    std::vector<size_t> lhs_stride;
    std::vector<size_t> rhs_stride;
    DenseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type);
    ~DenseJoinPlan();
    template <typename F> void execute(size_t lhs, size_t rhs, F &&f) const {
        switch(loops_left(0)) {
        case 0: return execute_few<F, 0>(0, lhs, rhs, std::forward<F>(f));
        case 1: return execute_few<F, 1>(0, lhs, rhs, std::forward<F>(f));
        case 2: return execute_few<F, 2>(0, lhs, rhs, std::forward<F>(f));
        case 3: return execute_few<F, 3>(0, lhs, rhs, std::forward<F>(f));
        default: return execute_many<F>(0, lhs, rhs, std::forward<F>(f));
        }
    }
private:
    size_t loops_left(size_t idx) const { return (loop_cnt.size() - idx); }
    template <typename F, size_t N> void execute_few(size_t idx, size_t lhs, size_t rhs, F &&f) const {
        if constexpr (N == 0) {
            f(lhs, rhs);
        } else {
            for (size_t i = 0; i < loop_cnt[idx]; ++i, lhs += lhs_stride[idx], rhs += rhs_stride[idx]) {
                execute_few<F, N - 1>(idx + 1, lhs, rhs, std::forward<F>(f));
            }
        }
    }
    template <typename F> void execute_many(size_t idx, size_t lhs, size_t rhs, F &&f) const {
        for (size_t i = 0; i < loop_cnt[idx]; ++i, lhs += lhs_stride[idx], rhs += rhs_stride[idx]) {
            if (loops_left(idx + 1) == 3) {
                execute_few<F, 3>(idx + 1, lhs, rhs, std::forward<F>(f));
            } else {
                execute_many<F>(idx + 1, lhs, rhs, std::forward<F>(f));
            }
        }
    }
};

/**
 * Plan for how to join the sparse part (all mapped dimensions)
 * between two values. The plan can be made up-front during tensor
 * function compilation.
 **/
struct SparseJoinPlan {
    enum class Source { LHS, RHS, BOTH };
    std::vector<Source> sources;
    std::vector<size_t> lhs_overlap;
    std::vector<size_t> rhs_overlap;
    SparseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type);
    ~SparseJoinPlan();
};

//-----------------------------------------------------------------------------

} // namespace
