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

void my_dot_product_op(eval::InterpretedFunction::State &state, uint64_t param) {
    auto *hw_accelerator = (hwaccelrated::IAccelrated *)(param);
    DenseTensorView::CellsRef lhsCells = getCellsRef(state.peek(1));
    DenseTensorView::CellsRef rhsCells = getCellsRef(state.peek(0));
    size_t numCells = std::min(lhsCells.size(), rhsCells.size());
    double result = hw_accelerator->dotProduct(lhsCells.cbegin(), rhsCells.cbegin(), numCells);
    state.pop_pop_push(state.stash.create<eval::DoubleValue>(result));
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
    return eval::InterpretedFunction::Instruction(my_dot_product_op, (uint64_t)(_hwAccelerator.get()));
}

bool
DenseDotProductFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    if (!res.is_double() || !lhs.is_dense() || !rhs.is_dense() ||
        (lhs.dimensions().size() != rhs.dimensions().size()) ||
        (lhs.dimensions().empty()))
    {
        return false;
    }
    for (size_t i = 0; i < lhs.dimensions().size(); ++i) {
        const auto &ldim = lhs.dimensions()[i];
        const auto &rdim = rhs.dimensions()[i];
        bool first = (i == 0);
        bool name_mismatch = (ldim.name != rdim.name);
        bool size_mismatch = ((ldim.size != rdim.size) || !ldim.is_bound());
        if (name_mismatch || (!first && size_mismatch)) {
            return false;
        }
    }
    return true;
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
