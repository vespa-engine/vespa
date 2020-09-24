// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_instructions.h"
#include "tensor_plans.h"
#include "inline_operation.h"
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval::tensor_instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

//-----------------------------------------------------------------------------

namespace {

//-----------------------------------------------------------------------------

template <typename T, typename IN> uint64_t wrap_param(const IN &value_in) {
    const T &value = value_in;
    static_assert(sizeof(uint64_t) == sizeof(&value));
    return (uint64_t)&value;
}

template <typename T> const T &unwrap_param(uint64_t param) {
    return *((const T *)param);
}

//-----------------------------------------------------------------------------

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
JoinParam::~JoinParam() = default;

//-----------------------------------------------------------------------------

// Contains various state needed to perform the sparse part (all
// mapped dimensions) of the join operation. Performs swapping of
// sparse indexes to ensure that we look up entries from the smallest
// index in the largest index.
struct SparseJoinState {
    bool                                    swapped;
    const Value::Index                     &first_index;
    const Value::Index                     &second_index;
    const std::vector<size_t>              &second_view_dims;
    std::vector<vespalib::stringref>        full_address;
    std::vector<vespalib::stringref*>       first_address;
    std::vector<const vespalib::stringref*> address_overlap;
    std::vector<vespalib::stringref*>       second_only_address;
    size_t                                  lhs_subspace;
    size_t                                  rhs_subspace;
    size_t                                 &first_subspace;
    size_t                                 &second_subspace;

    SparseJoinState(const SparseJoinPlan &plan, const Value::Index &lhs, const Value::Index &rhs)
        : swapped(rhs.size() < lhs.size()),
          first_index(swapped ? rhs : lhs), second_index(swapped ? lhs : rhs),
          second_view_dims(swapped ? plan.lhs_overlap : plan.rhs_overlap),
          full_address(plan.sources.size()),
          first_address(), address_overlap(), second_only_address(),
          lhs_subspace(), rhs_subspace(),
          first_subspace(swapped ? rhs_subspace : lhs_subspace),
          second_subspace(swapped ? lhs_subspace : rhs_subspace)
    {
        auto first_source = swapped ? SparseJoinPlan::Source::RHS : SparseJoinPlan::Source::LHS;
        for (size_t i = 0; i < full_address.size(); ++i) {
            if (plan.sources[i] == SparseJoinPlan::Source::BOTH) {
                first_address.push_back(&full_address[i]);
                address_overlap.push_back(&full_address[i]);
            } else if (plan.sources[i] == first_source) {
                first_address.push_back(&full_address[i]);
            } else {
                second_only_address.push_back(&full_address[i]);
            }
        }
    }
    ~SparseJoinState();
};
SparseJoinState::~SparseJoinState() = default;

template <typename LCT, typename RCT, typename OCT, typename Fun>
void my_generic_join(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<JoinParam>(param_in);
    Fun fun(param.function);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    auto lhs_cells = lhs.cells().typify<LCT>();
    auto rhs_cells = rhs.cells().typify<RCT>();
    SparseJoinState sparse(param.sparse_plan, lhs.index(), rhs.index());
    auto builder = param.factory.create_value_builder<OCT>(param.res_type, param.sparse_plan.sources.size(), param.dense_plan.out_size, sparse.first_index.size());
    auto outer = sparse.first_index.create_view({});
    auto inner = sparse.second_index.create_view(sparse.second_view_dims);
    outer->lookup({});
    while (outer->next_result(sparse.first_address, sparse.first_subspace)) {
        inner->lookup(sparse.address_overlap);
        while (inner->next_result(sparse.second_only_address, sparse.second_subspace)) {
            OCT *dst = builder->add_subspace(sparse.full_address).begin();
            auto join_cells = [&](size_t lhs_idx, size_t rhs_idx) { *dst++ = fun(lhs_cells[lhs_idx], rhs_cells[rhs_idx]); };
            param.dense_plan.execute(param.dense_plan.lhs_size * sparse.lhs_subspace, param.dense_plan.rhs_size * sparse.rhs_subspace, join_cells);
        }
    }
    auto &result = state.stash.create<std::unique_ptr<Value>>(builder->build(std::move(builder)));
    const Value &result_ref = *(result.get());
    state.pop_pop_push(result_ref);
};

struct SelectGenericJoin {
    template <typename LCT, typename RCT, typename OCT, typename Fun> static auto invoke() {
        return my_generic_join<LCT,RCT,OCT,Fun>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

using JoinTypify = TypifyValue<TypifyCellType,operation::TypifyOp2>;

Instruction make_join(const ValueType &lhs_type, const ValueType &rhs_type, join_fun_t function,
                      const ValueBuilderFactory &factory, Stash &stash)
{
    auto &param = stash.create<JoinParam>(lhs_type, rhs_type, function, factory);
    auto fun = typify_invoke<4,JoinTypify,SelectGenericJoin>(lhs_type.cell_type(), rhs_type.cell_type(), param.res_type.cell_type(), function);
    return Instruction(fun, wrap_param<JoinParam>(param));
}

//-----------------------------------------------------------------------------

}
