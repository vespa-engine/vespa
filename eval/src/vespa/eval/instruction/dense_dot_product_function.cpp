// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dot_product_function.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <cblas.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

template <typename LCT, typename RCT>
void my_dot_product_op(InterpretedFunction::State &state, uint64_t) {
    auto lhs_cells = state.peek(1).cells().typify<LCT>();
    auto rhs_cells = state.peek(0).cells().typify<RCT>();
    double result = 0.0;
    const LCT *lhs = lhs_cells.cbegin();
    const RCT *rhs = rhs_cells.cbegin();
    for (size_t i = 0; i < lhs_cells.size(); ++i) {
        result += ((*lhs++) * (*rhs++));
    }
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

void my_cblas_double_dot_product_op(InterpretedFunction::State &state, uint64_t) {
    auto lhs_cells = state.peek(1).cells().typify<double>();
    auto rhs_cells = state.peek(0).cells().typify<double>();
    double result = cblas_ddot(lhs_cells.size(), lhs_cells.cbegin(), 1, rhs_cells.cbegin(), 1);
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

void my_cblas_float_dot_product_op(InterpretedFunction::State &state, uint64_t) {
    auto lhs_cells = state.peek(1).cells().typify<float>();
    auto rhs_cells = state.peek(0).cells().typify<float>();
    double result = cblas_sdot(lhs_cells.size(), lhs_cells.cbegin(), 1, rhs_cells.cbegin(), 1);
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

struct MyDotProductOp {
    template <typename LCT, typename RCT>
    static auto invoke() { return my_dot_product_op<LCT,RCT>; }
};

InterpretedFunction::op_function my_select(CellType lct, CellType rct) {
    if (lct == rct) {
        if (lct == CellType::DOUBLE) {
            return my_cblas_double_dot_product_op;
        }
        if (lct == CellType::FLOAT) {
            return my_cblas_float_dot_product_op;
        }
    }
    using MyTypify = TypifyCellType;
    return typify_invoke<2,MyTypify,MyDotProductOp>(lct, rct);
}

} // namespace <unnamed>

DenseDotProductFunction::DenseDotProductFunction(const TensorFunction &lhs_in,
                                                 const TensorFunction &rhs_in)
    : tensor_function::Op2(ValueType::double_type(), lhs_in, rhs_in)
{
}

InterpretedFunction::Instruction
DenseDotProductFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = my_select(lhs().result_type().cell_type(), rhs().result_type().cell_type());
    return InterpretedFunction::Instruction(op);
}

bool
DenseDotProductFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    return (res.is_double() && lhs.is_dense() && (rhs.dimensions() == lhs.dimensions()));
}

const TensorFunction &
DenseDotProductFunction::optimize(const TensorFunction &expr, Stash &stash)
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

} // namespace
