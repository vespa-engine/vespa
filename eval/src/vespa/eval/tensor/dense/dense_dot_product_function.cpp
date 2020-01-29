// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dot_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>

#include <openblas/cblas.h>

namespace vespalib::tensor {

using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using eval::Aggr;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

template <typename LCT, typename RCT>
void my_dot_product_op(eval::InterpretedFunction::State &state, uint64_t) {
    auto lhs_cells = DenseTensorView::typify_cells<LCT>(state.peek(1));
    auto rhs_cells = DenseTensorView::typify_cells<RCT>(state.peek(0));
    double result = 0.0;
    const LCT *lhs = lhs_cells.cbegin();
    const RCT *rhs = rhs_cells.cbegin();
    for (size_t i = 0; i < lhs_cells.size(); ++i) {
        result += ((*lhs++) * (*rhs++));
    }
    state.pop_pop_push(state.stash.create<eval::DoubleValue>(result));
}

void my_cblas_double_dot_product_op(eval::InterpretedFunction::State &state, uint64_t) {
    auto lhs_cells = DenseTensorView::typify_cells<double>(state.peek(1));
    auto rhs_cells = DenseTensorView::typify_cells<double>(state.peek(0));
    double result = cblas_ddot(lhs_cells.size(), lhs_cells.cbegin(), 1, rhs_cells.cbegin(), 1);
    state.pop_pop_push(state.stash.create<eval::DoubleValue>(result));
}

void my_cblas_float_dot_product_op(eval::InterpretedFunction::State &state, uint64_t) {
    auto lhs_cells = DenseTensorView::typify_cells<float>(state.peek(1));
    auto rhs_cells = DenseTensorView::typify_cells<float>(state.peek(0));
    double result = cblas_sdot(lhs_cells.size(), lhs_cells.cbegin(), 1, rhs_cells.cbegin(), 1);
    state.pop_pop_push(state.stash.create<eval::DoubleValue>(result));
}

struct MyDotProductOp {
    template <typename LCT, typename RCT>
    static auto get_fun() { return my_dot_product_op<LCT,RCT>; }
};

eval::InterpretedFunction::op_function my_select(CellType lct, CellType rct) {
    if (lct == rct) {
        if (lct == ValueType::CellType::DOUBLE) {
            return my_cblas_double_dot_product_op;
        }
        if (lct == ValueType::CellType::FLOAT) {
            return my_cblas_float_dot_product_op;
        }
    }
    return select_2<MyDotProductOp>(lct, rct);
}

} // namespace vespalib::tensor::<unnamed>

DenseDotProductFunction::DenseDotProductFunction(const eval::TensorFunction &lhs_in,
                                                 const eval::TensorFunction &rhs_in)
    : eval::tensor_function::Op2(eval::ValueType::double_type(), lhs_in, rhs_in)
{
}

eval::InterpretedFunction::Instruction
DenseDotProductFunction::compile_self(Stash &) const
{
    auto op = my_select(lhs().result_type().cell_type(), rhs().result_type().cell_type());
    return eval::InterpretedFunction::Instruction(op);
}

bool
DenseDotProductFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    return (res.is_double() && lhs.is_dense() && (rhs.dimensions() == lhs.dimensions()));
}

const TensorFunction &
DenseDotProductFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (compatible_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
                return stash.create<DenseDotProductFunction>(lhs, rhs);
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
