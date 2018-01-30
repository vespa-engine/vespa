// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_dot_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>

namespace vespalib::tensor {

using CellsRef = DenseTensorView::CellsRef;

DenseDotProductFunction::DenseDotProductFunction(const eval::TensorFunction &lhs_in,
                                                 const eval::TensorFunction &rhs_in)
    : eval::tensor_function::Op2(eval::ValueType::double_type(), lhs_in, rhs_in),
      _hwAccelerator(hwaccelrated::IAccelrated::getAccelrator())
{
}

namespace {

CellsRef
getCellsRef(const eval::Value &value)
{
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

}

eval::InterpretedFunction::Instruction
DenseDotProductFunction::compile_self(Stash &) const
{
    return eval::InterpretedFunction::Instruction(my_op, (uint64_t)(_hwAccelerator.get()));
}

const eval::Value &
DenseDotProductFunction::eval(const eval::TensorEngine &engine, const eval::LazyParams &params, Stash &stash) const
{
    DenseTensorView::CellsRef lhsCells = getCellsRef(lhs().eval(engine, params, stash));
    DenseTensorView::CellsRef rhsCells = getCellsRef(rhs().eval(engine, params, stash));
    size_t numCells = std::min(lhsCells.size(), rhsCells.size());
    double result = _hwAccelerator->dotProduct(lhsCells.cbegin(), rhsCells.cbegin(), numCells);
    return stash.create<eval::DoubleValue>(result);
}

}

