// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_inplace_join_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>

namespace vespalib::tensor {

using CellsRef = DenseTensorView::CellsRef;
using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using namespace eval::tensor_function;

namespace {

ArrayRef<double> getMutableCells(const eval::Value &value) {
    const DenseTensorView &denseTensor = static_cast<const DenseTensorView &>(value);
    return unconstify(denseTensor.cellsRef());
}

ConstArrayRef<double> getConstCells(const eval::Value &value) {
    const DenseTensorView &denseTensor = static_cast<const DenseTensorView &>(value);
    return denseTensor.cellsRef();
}

void my_inplace_left_join_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    join_fun_t function = (join_fun_t)param;
    ArrayRef<double> left_cells = getMutableCells(lhs);
    ConstArrayRef<double> right_cells = getConstCells(rhs);
    auto rhs_iter = right_cells.cbegin();
    for (double &cell: left_cells) {
        cell = function(cell, *rhs_iter);
        ++rhs_iter;
    }
    assert(rhs_iter == right_cells.cend());
    state.pop_pop_push(lhs);
}

void my_inplace_right_join_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    join_fun_t function = (join_fun_t)param;
    ConstArrayRef<double> left_cells = getConstCells(lhs);
    ArrayRef<double> right_cells = getMutableCells(rhs);
    auto lhs_iter = left_cells.cbegin();
    for (double &cell: right_cells) {
        cell = function(*lhs_iter, cell);
        ++lhs_iter;
    }
    assert(lhs_iter == left_cells.cend());
    state.pop_pop_push(rhs);
}

bool isConcreteDenseTensor(const ValueType &type) {
    return (type.is_dense() && !type.is_abstract());
}

} // namespace vespalib::tensor::<unnamed>


DenseInplaceJoinFunction::DenseInplaceJoinFunction(const eval::tensor_function::Join &orig, bool left_is_mutable)
    : eval::tensor_function::Op2(orig.result_type(), orig.lhs(), orig.rhs()),
      _function(orig.function()),
      _left_is_mutable(left_is_mutable)
{
}

DenseInplaceJoinFunction::~DenseInplaceJoinFunction()
{
}

eval::InterpretedFunction::Instruction
DenseInplaceJoinFunction::compile_self(Stash &) const
{
    if (_left_is_mutable) {
        return eval::InterpretedFunction::Instruction(my_inplace_left_join_op, (uint64_t)_function);
    } else {
        return eval::InterpretedFunction::Instruction(my_inplace_right_join_op, (uint64_t)_function);
    }
}

const TensorFunction &
DenseInplaceJoinFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if ((lhs.result_is_mutable() || rhs.result_is_mutable())
            && join->result_type() == lhs.result_type()
            && join->result_type() == rhs.result_type()
            && isConcreteDenseTensor(join->result_type()))
        {
            return stash.create<DenseInplaceJoinFunction>(*join, lhs.result_is_mutable());
        }
    }
    return expr;
}

} // namespace vespalib::tensor
