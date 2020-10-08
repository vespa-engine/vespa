// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_concat.h"
#include "generic_join.h"
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <cassert>

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

template <typename T, typename IN> uint64_t wrap_param(const IN &value_in) {
    const T &value = value_in;
    static_assert(sizeof(uint64_t) == sizeof(&value));
    return (uint64_t)&value;
}

template <typename T> const T &unwrap_param(uint64_t param) {
    return *((const T *)param);
}

struct ConcatParam
{
    ValueType res_type;
    SparseJoinPlan sparse_plan;
    DenseConcatPlan dense_plan;
    const ValueBuilderFactory &factory;

    ConcatParam(const ValueType &lhs_type, const ValueType &rhs_type,
                const vespalib::string &dimension, const ValueBuilderFactory &factory_in)
      : res_type(ValueType::concat(lhs_type, rhs_type, dimension)),
        sparse_plan(lhs_type, rhs_type),
        dense_plan(lhs_type, rhs_type, dimension, res_type),
        factory(factory_in)
    {
        assert(!res_type.is_error());
    }
};

template <typename LCT, typename RCT>
std::unique_ptr<Value>
generic_concat(const Value &a, const Value &b,
               const SparseJoinPlan &sparse_plan,
               const DenseConcatPlan &dense_plan,
               const ValueType &res_type, const ValueBuilderFactory &factory)
{
    using OCT = typename eval::UnifyCellTypes<LCT, RCT>::type;
    auto a_cells = a.cells().typify<LCT>();
    auto b_cells = b.cells().typify<RCT>();
    SparseJoinState sparse(sparse_plan, a.index(), b.index());
    auto builder = factory.create_value_builder<OCT>(res_type,
                                                     sparse_plan.sources.size(),
                                                     dense_plan.right.output_size,
                                                     sparse.first_index.size());
    auto outer = sparse.first_index.create_view({});
    auto inner = sparse.second_index.create_view(sparse.second_view_dims);
    outer->lookup({});
    while (outer->next_result(sparse.first_address, sparse.first_subspace)) {
        inner->lookup(sparse.address_overlap);
        while (inner->next_result(sparse.second_only_address, sparse.second_subspace)) {
            OCT *dst = builder->add_subspace(sparse.full_address).begin();
            {
                size_t left_input_offset = dense_plan.left.input_size * sparse.lhs_subspace;
                auto copy_left = [&](size_t in_idx, size_t out_idx) { dst[out_idx] = a_cells[in_idx]; };
                dense_plan.left.execute(left_input_offset, 0, copy_left);
            }
            {
                size_t right_input_offset = dense_plan.right.input_size * sparse.rhs_subspace;
                auto copy_right = [&](size_t in_idx, size_t out_idx) { dst[out_idx] = b_cells[in_idx]; };
                dense_plan.right.execute(right_input_offset, dense_plan.right_offset, copy_right);
            }
        }
    }
    return builder->build(std::move(builder));
}

template <typename LCT, typename RCT>
void my_generic_concat_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<ConcatParam>(param_in);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    auto res_value = generic_concat<LCT, RCT>(lhs, rhs, param.sparse_plan, param.dense_plan,
                                              param.res_type, param.factory);
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(res_value));
    const Value &result_ref = *(result.get());
    state.pop_pop_push(result_ref);
}

struct SelectGenericConcatOp {
    template <typename LCT, typename RCT> static auto invoke() {
        return my_generic_concat_op<LCT, RCT>;
    }
};

enum class Case { NONE, OUT, CONCAT, BOTH };

} // namespace <unnamed>

DenseConcatPlan::InOutLoop::InOutLoop(const ValueType &in_type,
                                      std::string concat_dimension,
                                      const ValueType &out_type)
    : input_size(0),
      output_size(0),
      next_offset(0)
{
    std::vector<size_t> out_loop_cnt;
    Case prev_case = Case::NONE;
    auto update_plan = [&](Case my_case, size_t in_size, size_t out_size, size_t in_val, size_t out_val) {
        if (my_case == prev_case) {
            assert(!out_loop_cnt.empty());
            in_loop_cnt.back() *= in_size;
            out_loop_cnt.back() *= out_size;
        } else {
            in_loop_cnt.push_back(in_size);
            out_loop_cnt.push_back(out_size);
            in_stride.push_back(in_val);
            out_stride.push_back(out_val);
            prev_case = my_case;
        }
    };
    auto visitor = overload
                   {
                       [&](visit_ranges_first, const auto &) { abort(); },
                       [&](visit_ranges_second, const auto &b) {
                           if (b.name == concat_dimension) { update_plan(Case::CONCAT,   1, b.size, 0, 1);
                                                    } else { update_plan(Case::OUT, b.size, b.size, 0, 1); }
                       },
                       [&](visit_ranges_both, const auto &a, const auto &b) {
                           if (b.name == concat_dimension) { update_plan(Case::CONCAT, a.size, b.size, 1, 1);
                                                    } else { update_plan(Case::BOTH,   a.size, b.size, 1, 1); }
                       }
                   };
    const auto input_dimensions = in_type.nontrivial_indexed_dimensions();
    const auto output_dimensions = out_type.nontrivial_indexed_dimensions();
    visit_ranges(visitor, input_dimensions.begin(), input_dimensions.end(), output_dimensions.begin(), output_dimensions.end(),
                 [](const auto &a, const auto &b){ return (a.name < b.name); });

    input_size = 1;
    output_size = 1;
    for (size_t i = in_loop_cnt.size(); i-- > 0; ) {
        if (in_stride[i] != 0) {
            in_stride[i] = input_size;
            input_size *= in_loop_cnt[i];
        }
        assert(out_stride[i] != 0);
        assert(out_loop_cnt[i] != 0);
        out_stride[i] = output_size;
        output_size *= out_loop_cnt[i];
        if (in_loop_cnt[i] != out_loop_cnt[i]) {
            assert(next_offset == 0);
            next_offset = in_loop_cnt[i] * out_stride[i];
        }
    }
    assert(next_offset != 0);
}

InterpretedFunction::Instruction
GenericConcat::make_instruction(const ValueType &lhs_type, const ValueType &rhs_type,
                                const vespalib::string &dimension,
                                const ValueBuilderFactory &factory, Stash &stash)
{
    auto &param = stash.create<ConcatParam>(lhs_type, rhs_type, dimension, factory);
    auto fun = typify_invoke<2,TypifyCellType,SelectGenericConcatOp>(
            lhs_type.cell_type(), rhs_type.cell_type());
    return Instruction(fun, wrap_param<ConcatParam>(param));
}

DenseConcatPlan::DenseConcatPlan(const ValueType &lhs_type,
                                 const ValueType &rhs_type, 
                                 std::string concat_dimension,
                                 const ValueType &out_type)
  : right_offset(0),
    left(lhs_type, concat_dimension, out_type),
    right(rhs_type, concat_dimension, out_type)
{
    const auto output_dimensions = out_type.nontrivial_indexed_dimensions();
    right_offset = left.next_offset;
    assert(right_offset > 0);
    assert(left.output_size  == right.output_size);
}

DenseConcatPlan::~DenseConcatPlan() = default;
DenseConcatPlan::InOutLoop::~InOutLoop() = default;

} // namespace
