// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dot_product_function.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

template <typename LCT, typename RCT>
void my_dot_product_op(InterpretedFunction::State &state, uint64_t) {
    auto lhs_cells = state.peek(1).cells().typify<LCT>();
    auto rhs_cells = state.peek(0).cells().typify<RCT>();
    double result = DotProduct<LCT,RCT>::apply(lhs_cells.cbegin(), rhs_cells.cbegin(), lhs_cells.size());
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

struct MyDotProductOp {
    template <typename LCT, typename RCT>
    static auto invoke() { return my_dot_product_op<LCT,RCT>; }
};

} // namespace <unnamed>

DenseDotProductFunction::DenseDotProductFunction(const TensorFunction &lhs_in,
                                                 const TensorFunction &rhs_in)
    : tensor_function::Op2(ValueType::double_type(), lhs_in, rhs_in)
{
}

InterpretedFunction::Instruction
DenseDotProductFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = typify_invoke<2,TypifyCellType,MyDotProductOp>(lhs().result_type().cell_type(),
                                                             rhs().result_type().cell_type());
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
