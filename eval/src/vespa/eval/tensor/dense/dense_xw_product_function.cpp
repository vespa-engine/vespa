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

#include <cblas.h>

namespace vespalib::tensor {

using eval::ValueType;
using eval::TensorFunction;
using eval::as;
using eval::Aggr;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

template <typename LCT, typename RCT, bool common_inner>
double my_dot_product(const LCT *lhs, const RCT *rhs, size_t vector_size, size_t result_size) {
    double result = 0.0;
    for (size_t i = 0; i < vector_size; ++i) {
        result += ((*lhs) * (*rhs));
        ++lhs;
        rhs += (common_inner ? 1 : result_size);
    }
    return result;
}

template <typename LCT, typename RCT, bool common_inner>
void my_xw_product_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const DenseXWProductFunction::Self &self = *((const DenseXWProductFunction::Self *)(param));
    using OCT = typename eval::UnifyCellTypes<LCT,RCT>::type;
    auto vector_cells = DenseTensorView::typify_cells<LCT>(state.peek(1));
    auto matrix_cells = DenseTensorView::typify_cells<RCT>(state.peek(0));
    auto dst_cells = state.stash.create_array<OCT>(self.result_size);
    OCT *dst = dst_cells.begin();
    const RCT *matrix = matrix_cells.cbegin();
    for (size_t i = 0; i < self.result_size; ++i) {
        *dst++ = my_dot_product<LCT,RCT,common_inner>(vector_cells.cbegin(), matrix, self.vector_size, self.result_size);
        matrix += (common_inner ? self.vector_size : 1);
    }
    state.pop_pop_push(state.stash.create<DenseTensorView>(self.result_type, TypedCells(dst_cells)));
}

template <bool common_inner>
void my_cblas_double_xw_product_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const DenseXWProductFunction::Self &self = *((const DenseXWProductFunction::Self *)(param));
    auto vector_cells = DenseTensorView::typify_cells<double>(state.peek(1));
    auto matrix_cells = DenseTensorView::typify_cells<double>(state.peek(0));
    auto dst_cells = state.stash.create_array<double>(self.result_size);
    cblas_dgemv(CblasRowMajor, common_inner ? CblasNoTrans : CblasTrans,
                common_inner ? self.result_size : self.vector_size,
                common_inner ? self.vector_size : self.result_size,
                1.0, matrix_cells.cbegin(), common_inner ? self.vector_size : self.result_size, vector_cells.cbegin(), 1,
                0.0, dst_cells.begin(), 1);
    state.pop_pop_push(state.stash.create<DenseTensorView>(self.result_type, TypedCells(dst_cells)));
}

template <bool common_inner>
void my_cblas_float_xw_product_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const DenseXWProductFunction::Self &self = *((const DenseXWProductFunction::Self *)(param));
    auto vector_cells = DenseTensorView::typify_cells<float>(state.peek(1));
    auto matrix_cells = DenseTensorView::typify_cells<float>(state.peek(0));
    auto dst_cells = state.stash.create_array<float>(self.result_size);
    cblas_sgemv(CblasRowMajor, common_inner ? CblasNoTrans : CblasTrans,
                common_inner ? self.result_size : self.vector_size,
                common_inner ? self.vector_size : self.result_size,
                1.0, matrix_cells.cbegin(), common_inner ? self.vector_size : self.result_size, vector_cells.cbegin(), 1,
                0.0, dst_cells.begin(), 1);
    state.pop_pop_push(state.stash.create<DenseTensorView>(self.result_type, TypedCells(dst_cells)));
}

template <bool common_inner>
struct MyXWProductOp {
    template <typename LCT, typename RCT>
    static auto get_fun() { return my_xw_product_op<LCT,RCT,common_inner>; }
};

template <bool common_inner>
eval::InterpretedFunction::op_function my_select2(CellType lct, CellType rct) {
    if (lct == rct) {
        if (lct == ValueType::CellType::DOUBLE) {
            return my_cblas_double_xw_product_op<common_inner>;
        }
        if (lct == ValueType::CellType::FLOAT) {
            return my_cblas_float_xw_product_op<common_inner>;
        }
    }
    return select_2<MyXWProductOp<common_inner>>(lct, rct);
}

eval::InterpretedFunction::op_function my_select(CellType lct, CellType rct, bool common_inner) {
    if (common_inner) {
        return my_select2<true>(lct, rct);
    } else {
        return my_select2<false>(lct, rct);
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
    bool common_inner = (mat.result_type().dimension_index(vec.result_type().dimensions()[0].name) == 1);
    return stash.create<DenseXWProductFunction>(res, vec, mat,
                                                vec.result_type().dimensions()[0].size,
                                                res.dimensions()[0].size,
                                                common_inner);
}

} // namespace vespalib::tensor::<unnamed>

DenseXWProductFunction::Self::Self(const eval::ValueType &result_type_in,
                                   size_t vector_size_in, size_t result_size_in)
    : result_type(result_type_in),
      vector_size(vector_size_in),
      result_size(result_size_in)
{
}
DenseXWProductFunction::Self::~Self() = default;

DenseXWProductFunction::DenseXWProductFunction(const eval::ValueType &result_type,
                                               const eval::TensorFunction &vector_in,
                                               const eval::TensorFunction &matrix_in,
                                               size_t vector_size,
                                               size_t result_size,
                                               bool common_inner)
    : eval::tensor_function::Op2(result_type, vector_in, matrix_in),
      _vector_size(vector_size),
      _result_size(result_size),
      _common_inner(common_inner)
{
}

eval::InterpretedFunction::Instruction
DenseXWProductFunction::compile_self(Stash &stash) const
{
    Self &self = stash.create<Self>(result_type(), _vector_size, _result_size);
    auto op = my_select(lhs().result_type().cell_type(),
                        rhs().result_type().cell_type(), _common_inner);
    return eval::InterpretedFunction::Instruction(op, (uint64_t)(&self));
}

void
DenseXWProductFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitInt("vector_size", _vector_size);
    visitor.visitInt("result_size", _result_size);
    visitor.visitBool("common_inner", _common_inner);
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
