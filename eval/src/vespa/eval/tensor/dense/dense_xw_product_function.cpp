// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_xw_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <assert.h>

namespace vespalib::tensor {

DenseXWProductFunction::DenseXWProductFunction(const eval::ValueType &resultType,
                                               size_t vectorId,
                                               size_t matrixId,
                                               size_t vectorSize,
                                               size_t resultSize,
                                               bool matrixHasCommonDimensionInnermost)
    : _resultType(resultType),
      _vectorId(vectorId),
      _matrixId(matrixId),
      _vectorSize(vectorSize),
      _resultSize(resultSize),
      _commonDimensionInnermost(matrixHasCommonDimensionInnermost),
      _hwAccelerator(hwaccelrated::IAccelrated::getAccelrator())
{}

void
DenseXWProductFunction::multiDotProduct(const XWInput &vectorCells,
                                        const XWInput &matrixCells,
                                        XWOutput &result) const
{
    double *out = result.begin();
    const double *matrixP = matrixCells.cbegin();
    const double * const vectorP = vectorCells.cbegin();
    for (size_t row = 0; row < _resultSize; ++row) {
        double cell = _hwAccelerator->dotProduct(vectorP, matrixP, _vectorSize);
        *out++ = cell;
        matrixP += _vectorSize;
    }
    assert(out == result.end());
    assert(matrixP == matrixCells.cend());
}

void
DenseXWProductFunction::transposedProduct(const XWInput &vectorCells,
                                          const XWInput &matrixCells,
                                          XWOutput &result) const
{
    double *out = result.begin();
    const double * const matrixP = matrixCells.cbegin();
    const double * const vectorP = vectorCells.cbegin();
    for (size_t row = 0; row < _resultSize; ++row) {
        double cell = 0;
        for (size_t col = 0; col < _vectorSize; ++col) {
            cell += matrixP[col*_resultSize + row] * vectorP[col];
        }
        *out++ = cell;
    }
    assert(out == result.end());
}

namespace {

DenseTensorView::CellsRef
getCellsRef(const eval::Value &value)
{
    const DenseTensorView &denseTensor = static_cast<const DenseTensorView &>(value);
    return denseTensor.cellsRef();
}

void op_call_leaf_eval(eval::InterpretedFunction::State &state, uint64_t param) {
    DenseXWProductFunction *self = (DenseXWProductFunction *)(param);
    state.stack.push_back(self->eval(state.engine, *state.params, state.stash));
}

} // namespace <unnamed>

eval::InterpretedFunction::Instruction
DenseXWProductFunction::compile_self(Stash &) const
{
    return eval::InterpretedFunction::Instruction(op_call_leaf_eval, (uint64_t)(this));
}

const eval::Value &
DenseXWProductFunction::eval(const eval::TensorEngine &, const eval::LazyParams &params, Stash &stash) const
{
    DenseTensorView::CellsRef vectorCells = getCellsRef(params.resolve(_vectorId, stash));
    DenseTensorView::CellsRef matrixCells = getCellsRef(params.resolve(_matrixId, stash));

    ArrayRef<double> outputCells = stash.create_array<double>(_resultSize);
    if (_commonDimensionInnermost) {
        multiDotProduct(vectorCells, matrixCells, outputCells);
    } else {
        transposedProduct(vectorCells, matrixCells, outputCells);
    }
    return stash.create<DenseTensorView>(_resultType, outputCells);
}

}

