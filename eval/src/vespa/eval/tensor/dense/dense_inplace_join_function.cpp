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

CellsRef getCellsRef(const eval::Value &value) {
    const DenseTensorView &denseTensor = static_cast<const DenseTensorView &>(value);
    return denseTensor.cellsRef();
}

template <bool write_left>
void my_inplace_join_op(eval::InterpretedFunction::State &state, uint64_t param) {
    join_fun_t function = (join_fun_t)param;
    CellsRef lhs_cells = getCellsRef(state.peek(1));
    CellsRef rhs_cells = getCellsRef(state.peek(0));
    auto dst_cells = unconstify(write_left ? lhs_cells : rhs_cells);
    for (size_t i = 0; i < dst_cells.size(); ++i) {
        dst_cells[i] = function(lhs_cells[i], rhs_cells[i]);
    }
    if (write_left) {
        state.stack.pop_back();
    } else {
        const Value &result = state.stack.back();
        state.pop_pop_push(result);
    }
}

bool sameShapeConcreteDenseTensors(const ValueType &a, const ValueType &b) {
    return (a.is_dense() && !a.is_abstract() && (a == b));
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
    auto op = _write_left ? my_inplace_join_op<true> : my_inplace_join_op<false>;
    return eval::InterpretedFunction::Instruction(op, (uint64_t)function());
}

void
DenseInplaceJoinFunction::dump_tree(eval::DumpTarget &target) const
{
    target.node("DenseInplaceJoin replacing:");
    Join::dump_tree(target);
}

const TensorFunction &
DenseInplaceJoinFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if ((lhs.result_is_mutable() || rhs.result_is_mutable()) &&
            sameShapeConcreteDenseTensors(lhs.result_type(), rhs.result_type()))
        {
            return stash.create<DenseInplaceJoinFunction>(join->result_type(), lhs, rhs,
                    join->function(), lhs.result_is_mutable());
        }
    }
    return expr;
}

} // namespace vespalib::tensor
