// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dot_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>

namespace vespalib::tensor {

using CellsRef = DenseTensorView::CellsRef;
using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using eval::Aggr;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

CellsRef getCellsRef(const eval::Value &value) {
    const DenseTensorView &denseTensor = static_cast<const DenseTensorView &>(value);
    return denseTensor.cellsRef();
}

void my_op(eval::InterpretedFunction::State &state, uint64_t param) {
    auto *hw_accelerator = (hwaccelrated::IAccelrated *)(param);
    DenseTensorView::CellsRef lhsCells = getCellsRef(state.peek(1));
    DenseTensorView::CellsRef rhsCells = getCellsRef(state.peek(0));
    size_t numCells = std::min(lhsCells.size(), rhsCells.size());
    double result = hw_accelerator->dotProduct(lhsCells.cbegin(), rhsCells.cbegin(), numCells);
    state.pop_pop_push(state.stash.create<eval::DoubleValue>(result));
}

bool is1dDenseTensor(const ValueType &type) {
    return (type.is_dense() && (type.dimensions().size() == 1));
}

bool isDenseDotProduct(const ValueType &res, const ValueType &lhsType, const ValueType &rhsType) {
    return (res.is_double() &&
            is1dDenseTensor(lhsType) &&
            is1dDenseTensor(rhsType) &&
            (lhsType.dimensions()[0].name == rhsType.dimensions()[0].name));
}

} // namespace vespalib::tensor::<unnamed>

DenseDotProductFunction::DenseDotProductFunction(const eval::TensorFunction &lhs_in,
                                                 const eval::TensorFunction &rhs_in)
    : eval::tensor_function::Op2(eval::ValueType::double_type(), lhs_in, rhs_in),
      _hwAccelerator(hwaccelrated::IAccelrated::getAccelrator())
{
}

eval::InterpretedFunction::Instruction
DenseDotProductFunction::compile_self(Stash &) const
{
    return eval::InterpretedFunction::Instruction(my_op, (uint64_t)(_hwAccelerator.get()));
}

const TensorFunction &
DenseDotProductFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    const Reduce *reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM)) {
        const Join *join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (isDenseDotProduct(expr.result_type(), lhs.result_type(), rhs.result_type())) {
                return stash.create<DenseDotProductFunction>(lhs, rhs);
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
