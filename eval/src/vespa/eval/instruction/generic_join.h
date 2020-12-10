// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/nested_loop.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/interpreted_function.h>

namespace vespalib { class Stash; }
namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

using join_fun_t = operation::op2_t;

//-----------------------------------------------------------------------------

struct GenericJoin {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &lhs_type, const ValueType &rhs_type,
                     join_fun_t function,
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

// Contains various state needed to perform the sparse part (all
// mapped dimensions) of the join operation. Performs swapping of
// sparse indexes to ensure that we look up entries from the smallest
// index in the largest index.
struct SparseJoinState {
    bool                                    swapped;
    const Value::Index                     &first_index;
    const Value::Index                     &second_index;
    const std::vector<size_t>              &second_view_dims;
    std::vector<label_t>                    full_address;
    std::vector<label_t*>                   first_address;
    std::vector<const label_t*>             address_overlap;
    std::vector<label_t*>                   second_only_address;
    size_t                                  lhs_subspace;
    size_t                                  rhs_subspace;
    size_t                                 &first_subspace;
    size_t                                 &second_subspace;

    SparseJoinState(const SparseJoinPlan &plan, const Value::Index &lhs, const Value::Index &rhs);
    ~SparseJoinState();
};

/**
 * Full set of parameters passed to low-level generic join function
 **/
struct JoinParam {
    ValueType res_type;
    SparseJoinPlan sparse_plan;
    DenseJoinPlan dense_plan;
    join_fun_t function;
    const ValueBuilderFactory &factory;
    JoinParam(const ValueType &lhs_type, const ValueType &rhs_type,
             join_fun_t function_in, const ValueBuilderFactory &factory_in)
        : res_type(ValueType::join(lhs_type, rhs_type)),
          sparse_plan(lhs_type, rhs_type),
          dense_plan(lhs_type, rhs_type),
          function(function_in),
          factory(factory_in)
    {
        assert(!res_type.is_error());
    }
    ~JoinParam();
};

//-----------------------------------------------------------------------------

} // namespace
