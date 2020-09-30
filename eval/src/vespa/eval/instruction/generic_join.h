// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/nested_loop.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/interpreted_function.h>

namespace vespalib { class Stash; }
namespace vespalib::eval { struct ValueBuilderFactory; }

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
    template <typename F> void execute(size_t lhs, size_t rhs, const F &f) const {
        run_nested_loop(lhs, rhs, loop_cnt, lhs_stride, rhs_stride, f);
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
