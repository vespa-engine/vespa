// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "universal_dot_product.h"
#include "sparse_join_reduce_plan.h"
#include "dense_join_reduce_plan.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/fast_value.hpp>

namespace vespalib::eval {

using namespace tensor_function;
using namespace instruction;
using namespace operation;

namespace {

struct UniversalDotProductParam {
    ValueType            res_type;
    SparseJoinReducePlan sparse_plan;
    DenseJoinReducePlan  dense_plan;
    size_t               vector_size;

    UniversalDotProductParam(const ValueType &res_type_in,
                             const ValueType &lhs_type,
                             const ValueType &rhs_type)
      : res_type(res_type_in),
        sparse_plan(lhs_type, rhs_type, res_type),
        dense_plan(lhs_type, rhs_type, res_type),
        vector_size(1)
    {
        if (!dense_plan.loop_cnt.empty() &&
            dense_plan.lhs_stride.back() == 1 &&
            dense_plan.rhs_stride.back() == 1 &&
            dense_plan.res_stride.back() == 0)
        {
            vector_size = dense_plan.loop_cnt.back();
            dense_plan.loop_cnt.pop_back();
            dense_plan.lhs_stride.pop_back();
            dense_plan.rhs_stride.pop_back();
            dense_plan.res_stride.pop_back();
        }
    }
};

template <typename LCT, typename RCT, typename OCT>
void my_universal_dot_product_op(InterpretedFunction::State &state, uint64_t param_in) {
    using dot_product = DotProduct<LCT,RCT>;
    const auto &param = unwrap_param<UniversalDotProductParam>(param_in);
    const auto &lhs = state.peek(1);
    const auto &rhs = state.peek(0);
    const auto &lhs_index = lhs.index();
    const auto &rhs_index = rhs.index();
    const auto lhs_cells = lhs.cells().typify<LCT>();
    const auto rhs_cells = rhs.cells().typify<RCT>();
    auto &stored_result = state.stash.create<std::unique_ptr<FastValue<OCT,true>>>(
        std::make_unique<FastValue<OCT,true>>(param.res_type, param.sparse_plan.res_dims(), param.dense_plan.res_size,
                                              param.sparse_plan.estimate_result_size(lhs_index, rhs_index)));
    auto &result = *(stored_result.get());
    OCT *dst;
    auto dense_fun = [&](size_t lhs_idx, size_t rhs_idx, size_t dst_idx) {
                         dst[dst_idx] += dot_product::apply(&lhs_cells[lhs_idx], &rhs_cells[rhs_idx], param.vector_size);
                     };
    auto sparse_fun = [&](size_t lhs_subspace, size_t rhs_subspace, ConstArrayRef<string_id> res_addr) {
                          auto [space, first] = result.insert_subspace(res_addr);
                          if (first) {
                              std::fill(space.begin(), space.end(), OCT{});
                          }
                          dst = space.data();
                          param.dense_plan.execute(lhs_subspace * param.dense_plan.lhs_size,
                                                   rhs_subspace * param.dense_plan.rhs_size,
                                                   0, dense_fun);
                      };
    param.sparse_plan.execute(lhs_index, rhs_index, sparse_fun);
    if (result.my_index.map.size() == 0 && param.sparse_plan.res_dims() == 0) {
        auto [empty, ignore] = result.insert_subspace({});
        std::fill(empty.begin(), empty.end(), OCT{});
    }
    state.pop_pop_push(result);
}

template <typename OCT>
const Value &create_empty_result(const UniversalDotProductParam &param, Stash &stash) {
    if (param.sparse_plan.res_dims() == 0) {
        if (param.dense_plan.res_stride.empty()) {
            return stash.create<DoubleValue>(0.0);
        } else {
            auto zero_cells = stash.create_array<OCT>(param.dense_plan.res_size);
            return stash.create<ValueView>(param.res_type, TrivialIndex::get(), TypedCells(zero_cells));
        }
    } else {
        return stash.create<ValueView>(param.res_type, EmptyIndex::get(), TypedCells(nullptr, get_cell_type<OCT>(), 0));
    }
}

template <typename LCT, typename RCT, typename OCT>
void my_universal_forwarding_dot_product_op(InterpretedFunction::State &state, uint64_t param_in) {
    using dot_product = DotProduct<LCT,RCT>;
    const auto &param = unwrap_param<UniversalDotProductParam>(param_in);
    const auto &lhs = state.peek(1);
    const auto &rhs = state.peek(0);
    size_t lhs_index_size = lhs.index().size();
    size_t rhs_index_size = rhs.index().size();
    if (rhs_index_size == 0 || lhs_index_size == 0) {
        state.pop_pop_push(create_empty_result<OCT>(param, state.stash));
        return;
    }
    const auto lhs_cells = lhs.cells().typify<LCT>();
    const auto rhs_cells = rhs.cells().typify<RCT>();
    auto dst_cells = state.stash.create_array<OCT>(lhs_index_size * param.dense_plan.res_size);
    OCT *dst = dst_cells.data();
    auto dense_fun = [&](size_t lhs_idx, size_t rhs_idx, size_t dst_idx) {
                         dst[dst_idx] += dot_product::apply(&lhs_cells[lhs_idx], &rhs_cells[rhs_idx], param.vector_size);
                     };
    for (size_t lhs_subspace = 0; lhs_subspace < lhs_index_size; ++lhs_subspace) {
        for (size_t rhs_subspace = 0; rhs_subspace < rhs_index_size; ++rhs_subspace) {
            param.dense_plan.execute(lhs_subspace * param.dense_plan.lhs_size,
                                     rhs_subspace * param.dense_plan.rhs_size,
                                     lhs_subspace * param.dense_plan.res_size, dense_fun);
        }
    }
    const Value &result = state.stash.create<ValueView>(param.res_type, lhs.index(), TypedCells(dst_cells));
    state.pop_pop_push(result);
}

struct SelectUniversalDotProduct {
    template <typename LCM, typename RCM, typename SCALAR> static auto invoke(const UniversalDotProductParam &param) {
        constexpr CellMeta ocm = CellMeta::join(LCM::value, RCM::value).reduce(SCALAR::value);
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        if (param.sparse_plan.maybe_forward_lhs_index()) {
            return my_universal_forwarding_dot_product_op<LCT,RCT,OCT>;
        }
        return my_universal_dot_product_op<LCT,RCT,OCT>;
    }
};

bool check_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs) {
    (void) res;
    if (lhs.is_double() || rhs.is_double()) {
        return false;
    }
    if (lhs.count_mapped_dimensions() > 0 || rhs.count_mapped_dimensions() > 0) {
        return true;
    }
    return false;
}

} // namespace <unnamed>

UniversalDotProduct::UniversalDotProduct(const ValueType &res_type_in,
                                         const TensorFunction &lhs_in,
                                         const TensorFunction &rhs_in)
  : tensor_function::Op2(res_type_in, lhs_in, rhs_in)
{
}

InterpretedFunction::Instruction
UniversalDotProduct::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    auto &param = stash.create<UniversalDotProductParam>(result_type(), lhs().result_type(), rhs().result_type());
    using MyTypify = TypifyValue<TypifyCellMeta,TypifyBool>;
    auto op = typify_invoke<3,MyTypify,SelectUniversalDotProduct>(lhs().result_type().cell_meta(),
                                                                  rhs().result_type().cell_meta(),
                                                                  result_type().cell_meta().is_scalar,
                                                                  param);
    return InterpretedFunction::Instruction(op, wrap_param<UniversalDotProductParam>(param));
}

const TensorFunction &
UniversalDotProduct::optimize(const TensorFunction &expr, Stash &stash, bool force)
{
    if (auto reduce = as<Reduce>(expr); reduce && (reduce->aggr() == Aggr::SUM)) {
        if (auto join = as<Join>(reduce->child()); join && (join->function() == Mul::f)) {
            const ValueType &res_type = expr.result_type();
            const ValueType &lhs_type = join->lhs().result_type();
            const ValueType &rhs_type = join->rhs().result_type();
            if (force || check_types(res_type, lhs_type, rhs_type)) {
                SparseJoinReducePlan sparse_plan(lhs_type, rhs_type, res_type);
                if (sparse_plan.maybe_forward_rhs_index() && !sparse_plan.maybe_forward_lhs_index()) {
                    return stash.create<UniversalDotProduct>(res_type, join->rhs(), join->lhs());
                }
                return stash.create<UniversalDotProduct>(res_type, join->lhs(), join->rhs());
            }
        }
    }
    return expr;
}

} // namespace
