// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_inplace_map_function.h"
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

void my_inplace_map_op(eval::InterpretedFunction::State &state, uint64_t param) {
    map_fun_t function = (map_fun_t)param;
    for (double &cell: getMutableCells(state.peek(0))) {
        cell = function(cell);
    }
}

bool isConcreteDenseTensor(const ValueType &type) {
    return (type.is_dense() && !type.is_abstract());
}

} // namespace vespalib::tensor::<unnamed>

DenseInplaceMapFunction::DenseInplaceMapFunction(const eval::ValueType &result_type,
                                                 const eval::TensorFunction &child,
                                                 map_fun_t function_in)
    : eval::tensor_function::Map(result_type, child, function_in)
{
}

DenseInplaceMapFunction::~DenseInplaceMapFunction()
{
}

eval::InterpretedFunction::Instruction
DenseInplaceMapFunction::compile_self(Stash &) const
{
    return eval::InterpretedFunction::Instruction(my_inplace_map_op, (uint64_t)function());
}

void
DenseInplaceMapFunction::dump_tree(eval::DumpTarget &target) const
{
    target.node("DenseInplaceMap replacing:");
    Map::dump_tree(target);
}

const TensorFunction &
DenseInplaceMapFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
    if (auto map = as<Map>(expr)) {
        if (map->child().result_is_mutable() && isConcreteDenseTensor(map->result_type())) {
            return stash.create<DenseInplaceMapFunction>(map->result_type(), map->child(), map->function());
        }
    }
    return expr;
}

} // namespace vespalib::tensor
