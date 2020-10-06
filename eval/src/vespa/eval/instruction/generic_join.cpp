// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_join.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <cassert>

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

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


template <typename LCT, typename RCT, typename OCT, typename Fun>
void my_mixed_join_op(State &state, uint64_t param_in) {
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

template <typename LCT, typename RCT, typename OCT, typename Fun>
void my_dense_join_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<JoinParam>(param_in);
    Fun fun(param.function);
    auto lhs_cells = state.peek(1).cells().typify<LCT>();
    auto rhs_cells = state.peek(0).cells().typify<RCT>();
    ArrayRef<OCT> out_cells = state.stash.create_array<OCT>(param.dense_plan.out_size);
    OCT *dst = out_cells.begin();
    auto join_cells = [&](size_t lhs_idx, size_t rhs_idx) { *dst++ = fun(lhs_cells[lhs_idx], rhs_cells[rhs_idx]); };
    param.dense_plan.execute(0, 0, join_cells);
    state.pop_pop_push(state.stash.create<DenseValueView>(param.res_type, TypedCells(out_cells)));
};

template <typename Fun>
void my_double_join_op(State &state, uint64_t param_in) {
    Fun fun(unwrap_param<JoinParam>(param_in).function);
    state.pop_pop_push(state.stash.create<DoubleValue>(fun(state.peek(1).cells().typify<double>()[0],
                                                           state.peek(0).cells().typify<double>()[0])));
};

struct SelectGenericJoinOp {
    template <typename LCT, typename RCT, typename OCT, typename Fun> static auto invoke(const JoinParam &param) {
        if (param.res_type.is_double()) {
            bool all_double = (std::is_same_v<LCT, double> &&
                               std::is_same_v<RCT, double> &&
                               std::is_same_v<OCT, double>);
            assert(all_double);
            return my_double_join_op<Fun>;
        }
        if (param.sparse_plan.sources.empty()) {
            return my_dense_join_op<LCT,RCT,OCT,Fun>;
        }
        return my_mixed_join_op<LCT,RCT,OCT,Fun>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

//-----------------------------------------------------------------------------

DenseJoinPlan::DenseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type)
    : lhs_size(1), rhs_size(1), out_size(1), loop_cnt(), lhs_stride(), rhs_stride()
{
    enum class Case { NONE, LHS, RHS, BOTH };
    Case prev_case = Case::NONE;
    auto update_plan = [&](Case my_case, size_t my_size, size_t in_lhs, size_t in_rhs) {
        if (my_case == prev_case) {
            assert(!loop_cnt.empty());
            loop_cnt.back() *= my_size;
        } else {
            loop_cnt.push_back(my_size);
            lhs_stride.push_back(in_lhs);
            rhs_stride.push_back(in_rhs);
            prev_case = my_case;
        }
    };
    auto visitor = overload
                   {
                       [&](visit_ranges_first, const auto &a) { update_plan(Case::LHS, a.size, 1, 0); },
                       [&](visit_ranges_second, const auto &b) { update_plan(Case::RHS, b.size, 0, 1); },
                       [&](visit_ranges_both, const auto &a, const auto &) { update_plan(Case::BOTH, a.size, 1, 1); }
                   };
    auto lhs_dims = lhs_type.nontrivial_indexed_dimensions();
    auto rhs_dims = rhs_type.nontrivial_indexed_dimensions();
    visit_ranges(visitor, lhs_dims.begin(), lhs_dims.end(), rhs_dims.begin(), rhs_dims.end(),
                 [](const auto &a, const auto &b){ return (a.name < b.name); });
    for (size_t i = loop_cnt.size(); i-- > 0; ) {
        out_size *= loop_cnt[i];
        if (lhs_stride[i] != 0) {
            lhs_stride[i] = lhs_size;
            lhs_size *= loop_cnt[i];
        }
        if (rhs_stride[i] != 0) {
            rhs_stride[i] = rhs_size;
            rhs_size *= loop_cnt[i];
        }
    }
}

DenseJoinPlan::~DenseJoinPlan() = default;

//-----------------------------------------------------------------------------

SparseJoinPlan::SparseJoinPlan(const ValueType &lhs_type, const ValueType &rhs_type)
    : sources(), lhs_overlap(), rhs_overlap()
{
    size_t lhs_idx = 0;
    size_t rhs_idx = 0;
    auto visitor = overload
                   {
                       [&](visit_ranges_first, const auto &) {
                           sources.push_back(Source::LHS);
                           ++lhs_idx;
                       },
                       [&](visit_ranges_second, const auto &) {
                           sources.push_back(Source::RHS);
                           ++rhs_idx;
                       },
                       [&](visit_ranges_both, const auto &, const auto &) {
                           sources.push_back(Source::BOTH);
                           lhs_overlap.push_back(lhs_idx++);
                           rhs_overlap.push_back(rhs_idx++);
                       }
                   };
    auto lhs_dims = lhs_type.mapped_dimensions();
    auto rhs_dims = rhs_type.mapped_dimensions();
    visit_ranges(visitor, lhs_dims.begin(), lhs_dims.end(), rhs_dims.begin(), rhs_dims.end(),
                 [](const auto &a, const auto &b){ return (a.name < b.name); });
}

SparseJoinPlan::~SparseJoinPlan() = default;

//-----------------------------------------------------------------------------

SparseJoinState::SparseJoinState(const SparseJoinPlan &plan, const Value::Index &lhs, const Value::Index &rhs)
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

SparseJoinState::~SparseJoinState() = default;

//-----------------------------------------------------------------------------

using JoinTypify = TypifyValue<TypifyCellType,operation::TypifyOp2>;

Instruction
GenericJoin::make_instruction(const ValueType &lhs_type, const ValueType &rhs_type, join_fun_t function,
                              const ValueBuilderFactory &factory, Stash &stash)
{
    auto &param = stash.create<JoinParam>(lhs_type, rhs_type, function, factory);
    auto fun = typify_invoke<4,JoinTypify,SelectGenericJoinOp>(lhs_type.cell_type(), rhs_type.cell_type(), param.res_type.cell_type(), function, param);
    return Instruction(fun, wrap_param<JoinParam>(param));
}

} // namespace
