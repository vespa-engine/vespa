// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_xw_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <assert.h>

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

template <bool commonDimensionInnermost>
void my_op(eval::InterpretedFunction::State &state, uint64_t param) {
    DenseXWProductFunction::Self *self = (DenseXWProductFunction::Self *)(param);

    CellsRef vectorCells = getCellsRef(state.peek(1));
    CellsRef matrixCells = getCellsRef(state.peek(0));

    ArrayRef<double> outputCells = state.stash.create_array<double>(self->_resultSize);

    if (commonDimensionInnermost) {
        multiDotProduct(*self, vectorCells, matrixCells, outputCells);
    } else {
        transposedProduct(*self, vectorCells, matrixCells, outputCells);
    }
    state.pop_pop_push(state.stash.create<DenseTensorView>(self->_resultType, outputCells));
}

bool isConcreteDenseTensor(const ValueType &type, size_t d) {
    return (type.is_dense() && (type.dimensions().size() == d) && !type.is_abstract());
}

bool isDenseXWProduct(const ValueType &res, const ValueType &vec, const ValueType &mat) {
    if (isConcreteDenseTensor(res, 1) &&
        isConcreteDenseTensor(vec, 1) &&
        isConcreteDenseTensor(mat, 2))
    {
        size_t res_idx = mat.dimension_index(res.dimensions()[0].name);
        size_t vec_idx = mat.dimension_index(vec.dimensions()[0].name);
        size_t npos = ValueType::Dimension::npos;
        if ((res_idx != npos) && (vec_idx != npos) && (res_idx != vec_idx)) {
            return ((mat.dimensions()[res_idx].size == res.dimensions()[0].size) &&
                    (mat.dimensions()[vec_idx].size == vec.dimensions()[0].size));
        }
    }
    return false;
}

const TensorFunction &createDenseXWProduct(const ValueType &res, const TensorFunction &vec, const TensorFunction &mat, Stash &stash) {
    bool common_is_inner = (mat.result_type().dimension_index(vec.result_type().dimensions()[0].name) == 1);
    return stash.create<DenseXWProductFunction>(res, vec, mat,
                                                vec.result_type().dimensions()[0].size,
                                                res.dimensions()[0].size,
                                                common_is_inner);
}

} // namespace vespalib::tensor::<unnamed>

DenseXWProductFunction::Self::Self(const eval::ValueType &resultType,
                                   size_t vectorSize,
                                   size_t resultSize)
    : _resultType(resultType),
      _vectorSize(vectorSize),
      _resultSize(resultSize),
      _hwAccelerator(hwaccelrated::IAccelrated::getAccelrator())
{}

DenseXWProductFunction::DenseXWProductFunction(const eval::ValueType &resultType,
                                               const eval::TensorFunction &vector_in,
                                               const eval::TensorFunction &matrix_in,
                                               size_t vectorSize,
                                               size_t resultSize,
                                               bool matrixHasCommonDimensionInnermost)
    : eval::tensor_function::Op2(resultType, vector_in, matrix_in),
      _vectorSize(vectorSize),
      _resultSize(resultSize),
      _commonDimensionInnermost(matrixHasCommonDimensionInnermost)
{}

eval::InterpretedFunction::Instruction
DenseXWProductFunction::compile_self(Stash &stash) const
{
    Self &self = stash.create<Self>(result_type(), _vectorSize, _resultSize);
    auto op = _commonDimensionInnermost ? my_op<true> : my_op<false>;
    return eval::InterpretedFunction::Instruction(op, (uint64_t)(&self));
}

const TensorFunction &
DenseXWProductFunction::optimize(const eval::TensorFunction &expr, Stash &stash)
{
        const Reduce *reduce = as<Reduce>(expr);
        if (reduce && (reduce->aggr() == Aggr::SUM)) {
            const ValueType &result_type = reduce->result_type();
            const Join *join = as<Join>(reduce->child());
            if (join && (join->function() == Mul::f)) {
                const TensorFunction &lhs = join->lhs();
                const TensorFunction &rhs = join->rhs();
                if (isDenseXWProduct(result_type, lhs.result_type(), rhs.result_type())) {
                    return createDenseXWProduct(result_type, lhs, rhs, stash);
                }
                if (isDenseXWProduct(result_type, rhs.result_type(), lhs.result_type())) {
                    return createDenseXWProduct(result_type, rhs, lhs, stash);
                }
            }
        }
        return expr;
}

} // namespace vespalib::tensor
