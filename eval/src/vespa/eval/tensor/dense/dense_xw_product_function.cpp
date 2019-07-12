// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_xw_product_function.h"
#include "dense_tensor.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <assert.h>

namespace vespalib::tensor {

using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using eval::Aggr;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

template <typename LCT, typename RCT>
struct HWSupport {
    static double call(hwaccelrated::IAccelrated *, const LCT *lhs, const RCT *rhs, size_t len) {
        double result = 0.0;
        for (size_t i = 0; i < len; ++i) {
            result += (lhs[i] * rhs[i]);
        }
        return result;
    }
};
template <> struct HWSupport<float, float> {
    static double call(hwaccelrated::IAccelrated *hw, const float *lhs, const float *rhs, size_t len) {
        return hw->dotProduct(lhs, rhs, len);
    }
};
template <> struct HWSupport<double, double> {
    static double call(hwaccelrated::IAccelrated *hw, const double *lhs, const double *rhs, size_t len) {
        return hw->dotProduct(lhs, rhs, len);
    }
};

template <typename LCT, typename RCT, typename OCT>
void multiDotProduct(const DenseXWProductFunction::Self &self,
                     const ConstArrayRef<LCT> &vectorCells, const ConstArrayRef<RCT> &matrixCells, ArrayRef<OCT> &result)
{
    OCT *out = result.begin();
    const RCT *matrixP = matrixCells.cbegin();
    const LCT * const vectorP = vectorCells.cbegin();
    for (size_t row = 0; row < self._resultSize; ++row) {
        double cell = HWSupport<LCT,RCT>::call(self._hwAccelerator.get(), vectorP, matrixP, self._vectorSize);
        *out++ = cell;
        matrixP += self._vectorSize;
    }
    assert(out == result.end());
    assert(matrixP == matrixCells.cend());
}

template <typename LCT, typename RCT, typename OCT>
void transposedProduct(const DenseXWProductFunction::Self &self,
                       const ConstArrayRef<LCT> &vectorCells, const ConstArrayRef<RCT> &matrixCells, ArrayRef<OCT> &result)
{
    OCT *out = result.begin();
    const RCT * const matrixP = matrixCells.cbegin();
    const LCT * const vectorP = vectorCells.cbegin();
    for (size_t row = 0; row < self._resultSize; ++row) {
        double cell = 0;
        for (size_t col = 0; col < self._vectorSize; ++col) {
            cell += matrixP[col*self._resultSize + row] * vectorP[col];
        }
        *out++ = cell;
    }
    assert(out == result.end());
}

template <typename LCT, typename RCT, bool commonDimensionInnermost>
void my_xw_product_op(eval::InterpretedFunction::State &state, uint64_t param) {
    DenseXWProductFunction::Self *self = (DenseXWProductFunction::Self *)(param);

    using OCT = typename eval::UnifyCellTypes<LCT,RCT>::type;
    auto vectorCells = DenseTensorView::typify_cells<LCT>(state.peek(1));
    auto matrixCells = DenseTensorView::typify_cells<RCT>(state.peek(0));
    auto outputCells = state.stash.create_array<OCT>(self->_resultSize);

    if (commonDimensionInnermost) {
        multiDotProduct(*self, vectorCells, matrixCells, outputCells);
    } else {
        transposedProduct(*self, vectorCells, matrixCells, outputCells);
    }

    state.pop_pop_push(state.stash.create<DenseTensorView>(self->_resultType, TypedCells(outputCells)));
}

template <bool common_inner>
struct MyXWProductOp {
    template <typename LCT, typename RCT>
    static auto get_fun() { return my_xw_product_op<LCT,RCT,common_inner>; }
};

eval::InterpretedFunction::op_function my_select(CellType lct, CellType rct, bool common_innermost) {
    if (common_innermost) {
        return select_2<MyXWProductOp<true> >(lct, rct);
    } else {
        return select_2<MyXWProductOp<false> >(lct, rct);
    }
}

bool isDenseTensor(const ValueType &type, size_t d) {
    return (type.is_dense() && (type.dimensions().size() == d));
}

bool isDenseXWProduct(const ValueType &res, const ValueType &vec, const ValueType &mat) {
    if (isDenseTensor(res, 1) &&
        isDenseTensor(vec, 1) &&
        isDenseTensor(mat, 2))
    {
        size_t res_idx = mat.dimension_index(res.dimensions()[0].name);
        size_t vec_idx = mat.dimension_index(vec.dimensions()[0].name);
        size_t npos = ValueType::Dimension::npos;
        if ((res_idx != npos) && (vec_idx != npos) && (res_idx != vec_idx)) {
            assert(mat.dimensions()[res_idx].size == res.dimensions()[0].size);
            assert(mat.dimensions()[vec_idx].size == vec.dimensions()[0].size);
            return true;
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
    auto op = my_select(lhs().result_type().cell_type(),
                        rhs().result_type().cell_type(), _commonDimensionInnermost);
    return eval::InterpretedFunction::Instruction(op, (uint64_t)(&self));
}

void
DenseXWProductFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitInt("vector_size", _vectorSize);
    visitor.visitInt("result_size", _resultSize);
    visitor.visitBool("common_dimension_innermost", _commonDimensionInnermost);
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
