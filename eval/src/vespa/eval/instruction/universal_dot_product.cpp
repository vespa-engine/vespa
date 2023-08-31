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
    ArrayRef<OCT> dst;
    auto dense_fun = [&](size_t lhs_idx, size_t rhs_idx, size_t dst_idx) {
                         dst[dst_idx] += dot_product::apply(&lhs_cells[lhs_idx], &rhs_cells[rhs_idx], param.vector_size);
                     };
    auto sparse_fun = [&](size_t lhs_subspace, size_t rhs_subspace, ConstArrayRef<string_id> res_addr) {
                          bool first;
                          std::tie(dst, first) = result.insert_subspace(res_addr);
                          if (first) {
                              std::fill(dst.begin(), dst.end(), OCT{});
                          }
                          param.dense_plan.execute(lhs_subspace * param.dense_plan.lhs_size,
                                                   rhs_subspace * param.dense_plan.rhs_size,
                                                   0, dense_fun);
                      };
    param.sparse_plan.execute(lhs_index, rhs_index, sparse_fun);
    state.pop_pop_push(result);
}

struct SelectUniversalDotProduct {
    template <typename LCM, typename RCM, typename SCALAR> static auto invoke(const UniversalDotProductParam &) {
        constexpr CellMeta ocm = CellMeta::join(LCM::value, RCM::value).reduce(SCALAR::value);
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        return my_universal_dot_product_op<LCT,RCT,OCT>;
    }
};

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
UniversalDotProduct::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto reduce = as<Reduce>(expr); reduce && (reduce->aggr() == Aggr::SUM)) {
        if (auto join = as<Join>(reduce->child()); join && (join->function() == Mul::f)) {
            return stash.create<UniversalDotProduct>(expr.result_type(), join->lhs(), join->rhs());
        }
    }
    return expr;
}

} // namespace
