// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_concat.h"
#include "generic_join.h"
#include <vespa/eval/eval/value_builder_factory.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/visit_ranges.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

struct ConcatParam
{
    ValueType res_type;
    SparseJoinPlan sparse_plan;
    DenseConcatPlan dense_plan;
    const ValueBuilderFactory &factory;

    ConcatParam(const ValueType &res_type_in,
                const ValueType &lhs_type, const ValueType &rhs_type,
                const vespalib::string &dimension, const ValueBuilderFactory &factory_in)
      : res_type(res_type_in),
        sparse_plan(lhs_type, rhs_type),
        dense_plan(lhs_type, rhs_type, dimension, res_type),
        factory(factory_in)
    {
        assert(!res_type.is_error());
    }
};

template <typename LCT, typename RCT, typename OCT>
std::unique_ptr<Value>
generic_concat(const Value &a, const Value &b,
               const SparseJoinPlan &sparse_plan,
               const DenseConcatPlan &dense_plan,
               const ValueType &res_type, const ValueBuilderFactory &factory)
{
    auto a_cells = a.cells().typify<LCT>();
    auto b_cells = b.cells().typify<RCT>();
    SparseJoinState sparse(sparse_plan, a.index(), b.index());
    auto builder = factory.create_transient_value_builder<OCT>(res_type,
                                                               sparse_plan.sources.size(),
                                                               dense_plan.output_size,
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

template <typename LCT, typename RCT, typename OCT>
void my_generic_concat_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<ConcatParam>(param_in);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    auto res_value = generic_concat<LCT, RCT, OCT>(
            lhs, rhs,
            param.sparse_plan, param.dense_plan,
            param.res_type, param.factory);
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(res_value));
    const Value &result_ref = *(result.get());
    state.pop_pop_push(result_ref);
}

template <typename LCT, typename RCT, typename OCT, bool forward_lhs>
void my_mixed_dense_concat_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<ConcatParam>(param_in);
    const DenseConcatPlan &dense_plan = param.dense_plan;
    auto lhs_cells = state.peek(1).cells().typify<LCT>();
    auto rhs_cells = state.peek(0).cells().typify<RCT>();
    const auto &index = state.peek(forward_lhs ? 1 : 0).index();
    size_t num_subspaces = index.size();
    size_t num_out_cells = dense_plan.output_size * num_subspaces;
    ArrayRef<OCT> out_cells = state.stash.create_uninitialized_array<OCT>(num_out_cells);
    OCT *dst = out_cells.begin();
    const LCT *lhs = lhs_cells.begin();
    const RCT *rhs = rhs_cells.begin();
    auto copy_left = [&](size_t in_idx, size_t out_idx) { dst[out_idx] = lhs[in_idx]; };
    auto copy_right = [&](size_t in_idx, size_t out_idx) { dst[out_idx] = rhs[in_idx]; };
    for (size_t i = 0; i < num_subspaces; ++i) {
        dense_plan.left.execute(0, 0, copy_left);
        dense_plan.right.execute(0, dense_plan.right_offset, copy_right);
        if (forward_lhs) {
            lhs += dense_plan.left.input_size;
        } else {
            rhs += dense_plan.right.input_size;
        }
        dst += dense_plan.output_size;
    }
    if (forward_lhs) {
        assert(lhs == lhs_cells.end());
    } else {
        assert(rhs == rhs_cells.end());
    }
    assert(dst == out_cells.end());
    state.pop_pop_push(state.stash.create<ValueView>(param.res_type, index, TypedCells(out_cells)));
}

template <typename LCT, typename RCT, typename OCT>
void my_dense_simple_concat_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<ConcatParam>(param_in);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    const auto a = lhs.cells().typify<LCT>();
    const auto b = rhs.cells().typify<RCT>();
    ArrayRef<OCT> result = state.stash.create_uninitialized_array<OCT>(a.size() + b.size());
    auto pos = result.begin();
    for (size_t i = 0; i < a.size(); ++i) {
        *pos++ = a[i];
    }
    for (size_t i = 0; i < b.size(); ++i) {
        *pos++ = b[i];
    }
    Value &ref = state.stash.create<DenseValueView>(param.res_type, TypedCells(result));
    state.pop_pop_push(ref);
}

struct SelectGenericConcatOp {
    template <typename LCM, typename RCM> static auto invoke(const ConcatParam &param) {
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        constexpr CellMeta ocm = CellMeta::concat(LCM::value, RCM::value);
        using OCT = CellValueType<ocm.cell_type>;
        if (param.sparse_plan.sources.empty() && param.res_type.is_dense()) {
            auto & dp = param.dense_plan;
            if ((dp.output_size == (dp.left.input_size + dp.right.input_size))
                && (dp.right_offset == dp.left.input_size))
            {
                return my_dense_simple_concat_op<LCT, RCT, OCT>;
            }
        }
        if (param.sparse_plan.should_forward_lhs_index()) {
            return my_mixed_dense_concat_op<LCT, RCT, OCT, true>;
        }
        if (param.sparse_plan.should_forward_rhs_index()) {
            return my_mixed_dense_concat_op<LCT, RCT, OCT, false>;
        }
        return my_generic_concat_op<LCT, RCT, OCT>;
    }
};

enum class Case { NONE, OUT, CONCAT, BOTH };

} // namespace <unnamed>

std::pair<size_t, size_t>
DenseConcatPlan::InOutLoop::fill_from(const ValueType &in_type,
                                      std::string concat_dimension,
                                      const ValueType &out_type)
{
    SmallVector<size_t> out_loop_cnt;
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
    size_t output_size_for_concat = 1;
    size_t offset_for_concat = 0;
    for (size_t i = in_loop_cnt.size(); i-- > 0; ) {
        if (in_stride[i] != 0) {
            in_stride[i] = input_size;
            input_size *= in_loop_cnt[i];
        }
        assert(out_stride[i] != 0);
        assert(out_loop_cnt[i] != 0);
        out_stride[i] = output_size_for_concat;
        output_size_for_concat *= out_loop_cnt[i];
        // loop counts are different if and only if this is the concat dimension 
        if (in_loop_cnt[i] != out_loop_cnt[i]) {
            assert(offset_for_concat == 0);
            offset_for_concat = in_loop_cnt[i] * out_stride[i];
        }
    }
    assert(offset_for_concat != 0);
    return std::make_pair(offset_for_concat, output_size_for_concat);
}

DenseConcatPlan::DenseConcatPlan(const ValueType &lhs_type,
                                 const ValueType &rhs_type, 
                                 std::string concat_dimension,
                                 const ValueType &out_type)
{
    std::tie(right_offset, output_size) = left.fill_from(lhs_type, concat_dimension, out_type);
    auto [ other_offset, other_size ] = right.fill_from(rhs_type, concat_dimension, out_type);
    assert(other_offset > 0);
    assert(output_size == other_size);
}

DenseConcatPlan::~DenseConcatPlan() = default;
DenseConcatPlan::InOutLoop::~InOutLoop() = default;


InterpretedFunction::Instruction
GenericConcat::make_instruction(const ValueType &result_type,
                                const ValueType &lhs_type, const ValueType &rhs_type,
                                const vespalib::string &dimension,
                                const ValueBuilderFactory &factory, Stash &stash)
{
    auto &param = stash.create<ConcatParam>(result_type, lhs_type, rhs_type, dimension, factory);
    assert(result_type == ValueType::concat(lhs_type, rhs_type, dimension));
    auto fun = typify_invoke<2,TypifyCellMeta,SelectGenericConcatOp>(
            lhs_type.cell_meta(), rhs_type.cell_meta(),
            param);
    return Instruction(fun, wrap_param<ConcatParam>(param));
}

} // namespace
