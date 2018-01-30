// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_xw_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <assert.h>

namespace vespalib::tensor {

namespace {

DenseTensorView::CellsRef
getCellsRef(const eval::Value &value)
{
    const DenseTensorView &denseTensor = static_cast<const DenseTensorView &>(value);
    return denseTensor.cellsRef();
}

void multiDotProduct(const DenseXWProductFunction::Self &self,
                     const XWInput &vectorCells, const XWInput &matrixCells, XWOutput &result)
{
    double *out = result.begin();
    const double *matrixP = matrixCells.cbegin();
    const double * const vectorP = vectorCells.cbegin();
    for (size_t row = 0; row < self._resultSize; ++row) {
        double cell = self._hwAccelerator->dotProduct(vectorP, matrixP, self._vectorSize);
        *out++ = cell;
        matrixP += self._vectorSize;
    }
    assert(out == result.end());
    assert(matrixP == matrixCells.cend());
}

void transposedProduct(const DenseXWProductFunction::Self &self,
                       const XWInput &vectorCells, const XWInput &matrixCells, XWOutput &result)
{
    double *out = result.begin();
    const double * const matrixP = matrixCells.cbegin();
    const double * const vectorP = vectorCells.cbegin();
    for (size_t row = 0; row < self._resultSize; ++row) {
        double cell = 0;
        for (size_t col = 0; col < self._vectorSize; ++col) {
            cell += matrixP[col*self._resultSize + row] * vectorP[col];
        }
        *out++ = cell;
    }
    assert(out == result.end());
}

void my_op(eval::InterpretedFunction::State &state, uint64_t param) {
    DenseXWProductFunction::Self *self = (DenseXWProductFunction::Self *)(param);

    DenseTensorView::CellsRef vectorCells = getCellsRef(state.peek(1));
    DenseTensorView::CellsRef matrixCells = getCellsRef(state.peek(0));

    ArrayRef<double> outputCells = state.stash.create_array<double>(self->_resultSize);
    if (self->_commonDimensionInnermost) {
        multiDotProduct(*self, vectorCells, matrixCells, outputCells);
    } else {
        transposedProduct(*self, vectorCells, matrixCells, outputCells);
    }
    state.pop_pop_push(state.stash.create<DenseTensorView>(self->_resultType, outputCells));
}

} // namespace vespalib::tensor::<unnamed>

DenseXWProductFunction::Self::Self(const eval::ValueType &resultType,
                                   size_t vectorSize,
                                   size_t resultSize,
                                   bool matrixHasCommonDimensionInnermost)
    : _resultType(resultType),
      _vectorSize(vectorSize),
      _resultSize(resultSize),
      _commonDimensionInnermost(matrixHasCommonDimensionInnermost),
      _hwAccelerator(hwaccelrated::IAccelrated::getAccelrator())
{}

DenseXWProductFunction::DenseXWProductFunction(const eval::ValueType &resultType,
                                               const eval::TensorFunction &vector_in,
                                               const eval::TensorFunction &matrix_in,
                                               size_t vectorSize,
                                               size_t resultSize,
                                               bool matrixHasCommonDimensionInnermost)
    : eval::tensor_function::Op2(resultType, vector_in, matrix_in),
      _self(resultType, vectorSize, resultSize, matrixHasCommonDimensionInnermost)
{}

namespace {

} // namespace <unnamed>

eval::InterpretedFunction::Instruction
DenseXWProductFunction::compile_self(Stash &) const
{
    return eval::InterpretedFunction::Instruction(my_op, (uint64_t)(&_self));
}

const eval::Value &
DenseXWProductFunction::eval(const eval::TensorEngine &engine, const eval::LazyParams &params, Stash &stash) const
{
    DenseTensorView::CellsRef vectorCells = getCellsRef(lhs().eval(engine, params, stash));
    DenseTensorView::CellsRef matrixCells = getCellsRef(rhs().eval(engine, params, stash));

    ArrayRef<double> outputCells = stash.create_array<double>(_self._resultSize);
    if (_self._commonDimensionInnermost) {
        multiDotProduct(_self, vectorCells, matrixCells, outputCells);
    } else {
        transposedProduct(_self, vectorCells, matrixCells, outputCells);
    }
    return stash.create<DenseTensorView>(result_type(), outputCells);
}

}

