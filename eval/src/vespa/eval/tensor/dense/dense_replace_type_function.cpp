// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_replace_type_function.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>

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

void my_replace_type_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const ValueType *type = (const ValueType *)(param);
    CellsRef cells = getCellsRef(state.peek(0));
    state.pop_push(state.stash.create<DenseTensorView>(*type, cells));
}

} // namespace vespalib::tensor::<unnamed>

DenseReplaceTypeFunction::DenseReplaceTypeFunction(const eval::ValueType &result_type,
                                                   const eval::TensorFunction &child)
    : eval::tensor_function::Op1(result_type, child)
{
}

DenseReplaceTypeFunction::~DenseReplaceTypeFunction()
{
}

eval::InterpretedFunction::Instruction
DenseReplaceTypeFunction::compile_self(Stash &) const
{
    return eval::InterpretedFunction::Instruction(my_replace_type_op, (uint64_t)&(result_type()));
}

const DenseReplaceTypeFunction &
DenseReplaceTypeFunction::create_compact(const eval::ValueType &result_type,
                                         const eval::TensorFunction &child,
                                         Stash &stash)
{
    if (auto replace = as<DenseReplaceTypeFunction>(child)) {
        return stash.create<DenseReplaceTypeFunction>(result_type, replace->child());
    } else {
        return stash.create<DenseReplaceTypeFunction>(result_type, child);
    }
}

} // namespace vespalib::tensor
