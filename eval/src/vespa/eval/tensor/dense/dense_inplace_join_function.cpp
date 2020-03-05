// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_inplace_join_function.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>

namespace vespalib::tensor {

using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using namespace eval::tensor_function;

namespace {

template <typename LCT, typename RCT>
void my_inplace_join_left_op(eval::InterpretedFunction::State &state, uint64_t param) {
    join_fun_t function = (join_fun_t)param;
    auto lhs_cells = unconstify(DenseTensorView::typify_cells<LCT>(state.peek(1)));
    auto rhs_cells = DenseTensorView::typify_cells<RCT>(state.peek(0));
    for (size_t i = 0; i < lhs_cells.size(); ++i) {
        lhs_cells[i] = function(lhs_cells[i], rhs_cells[i]);
    }
    state.stack.pop_back();
}

template <typename LCT, typename RCT>
void my_inplace_join_right_op(eval::InterpretedFunction::State &state, uint64_t param) {
    join_fun_t function = (join_fun_t)param;
    auto lhs_cells = DenseTensorView::typify_cells<LCT>(state.peek(1));
    auto rhs_cells = unconstify(DenseTensorView::typify_cells<RCT>(state.peek(0)));
    for (size_t i = 0; i < rhs_cells.size(); ++i) {
        rhs_cells[i] = function(lhs_cells[i], rhs_cells[i]);
    }
    const Value &result = state.stack.back();
    state.pop_pop_push(result);
}

struct MyInplaceJoinLeftOp {
    template <typename LCT, typename RCT>
    static auto get_fun() { return my_inplace_join_left_op<LCT,RCT>; }
};

struct MyInplaceJoinRightOp {
    template <typename LCT, typename RCT>
    static auto get_fun() { return my_inplace_join_right_op<LCT,RCT>; }
};

eval::InterpretedFunction::op_function my_select(CellType lct, CellType rct, bool write_left) {
    if (write_left) {
        return select_2<MyInplaceJoinLeftOp>(lct, rct);
    } else {
        return select_2<MyInplaceJoinRightOp>(lct, rct);
    }
}

} // namespace vespalib::tensor::<unnamed>


DenseInplaceJoinFunction::DenseInplaceJoinFunction(const ValueType &result_type,
                                                   const TensorFunction &lhs,
                                                   const TensorFunction &rhs,
                                                   join_fun_t function_in,
                                                   bool write_left_in)
    : eval::tensor_function::Join(result_type, lhs, rhs, function_in),
      _write_left(write_left_in)
{
}

DenseInplaceJoinFunction::~DenseInplaceJoinFunction()
{
}

eval::InterpretedFunction::Instruction
DenseInplaceJoinFunction::compile_self(Stash &) const
{
    auto op = my_select(lhs().result_type().cell_type(),
                        rhs().result_type().cell_type(), _write_left);
    return eval::InterpretedFunction::Instruction(op, (uint64_t)function());
}

void
DenseInplaceJoinFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitBool("write_left", _write_left);
}

const TensorFunction &
DenseInplaceJoinFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (lhs.result_type().is_dense() &&
            (lhs.result_type().dimensions() == rhs.result_type().dimensions()))
        {
            if (lhs.result_is_mutable() && (lhs.result_type() == expr.result_type())) {
                return stash.create<DenseInplaceJoinFunction>(join->result_type(), lhs, rhs,
                        join->function(), /* write left: */ true);
            }
            if (rhs.result_is_mutable() && (rhs.result_type() == expr.result_type())) {
                return stash.create<DenseInplaceJoinFunction>(join->result_type(), lhs, rhs,
                        join->function(), /* write left: */ false);
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
