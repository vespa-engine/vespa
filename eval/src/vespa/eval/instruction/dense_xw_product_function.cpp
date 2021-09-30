// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_xw_product_function.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <cassert>

#include <cblas.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

template <typename LCT, typename RCT, typename OCT, bool common_inner>
OCT my_dot_product(const LCT *lhs, const RCT *rhs, size_t vector_size, size_t result_size) {
    OCT result = 0.0;
    for (size_t i = 0; i < vector_size; ++i) {
        result += ((*lhs) * (*rhs));
        ++lhs;
        rhs += (common_inner ? 1 : result_size);
    }
    return result;
}

template <typename LCT, typename RCT, typename OCT, bool common_inner>
void my_xw_product_op(InterpretedFunction::State &state, uint64_t param) {
    const DenseXWProductFunction::Self &self = unwrap_param<DenseXWProductFunction::Self>(param);
    auto vector_cells = state.peek(1).cells().typify<LCT>();
    auto matrix_cells = state.peek(0).cells().typify<RCT>();
    auto dst_cells = state.stash.create_uninitialized_array<OCT>(self.result_size);
    OCT *dst = dst_cells.begin();
    const RCT *matrix = matrix_cells.cbegin();
    for (size_t i = 0; i < self.result_size; ++i) {
        *dst++ = my_dot_product<LCT,RCT,OCT,common_inner>(vector_cells.cbegin(), matrix, self.vector_size, self.result_size);
        matrix += (common_inner ? self.vector_size : 1);
    }
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type, TypedCells(dst_cells)));
}

template <bool common_inner>
void my_cblas_double_xw_product_op(InterpretedFunction::State &state, uint64_t param) {
    const DenseXWProductFunction::Self &self = unwrap_param<DenseXWProductFunction::Self>(param);
    auto vector_cells = state.peek(1).cells().typify<double>();
    auto matrix_cells = state.peek(0).cells().typify<double>();
    auto dst_cells = state.stash.create_array<double>(self.result_size);
    cblas_dgemv(CblasRowMajor, common_inner ? CblasNoTrans : CblasTrans,
                common_inner ? self.result_size : self.vector_size,
                common_inner ? self.vector_size : self.result_size,
                1.0, matrix_cells.cbegin(), common_inner ? self.vector_size : self.result_size, vector_cells.cbegin(), 1,
                0.0, dst_cells.begin(), 1);
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type, TypedCells(dst_cells)));
}

template <bool common_inner>
void my_cblas_float_xw_product_op(InterpretedFunction::State &state, uint64_t param) {
    const DenseXWProductFunction::Self &self = unwrap_param<DenseXWProductFunction::Self>(param);
    auto vector_cells = state.peek(1).cells().typify<float>();
    auto matrix_cells = state.peek(0).cells().typify<float>();
    auto dst_cells = state.stash.create_array<float>(self.result_size);
    cblas_sgemv(CblasRowMajor, common_inner ? CblasNoTrans : CblasTrans,
                common_inner ? self.result_size : self.vector_size,
                common_inner ? self.vector_size : self.result_size,
                1.0, matrix_cells.cbegin(), common_inner ? self.vector_size : self.result_size, vector_cells.cbegin(), 1,
                0.0, dst_cells.begin(), 1);
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type, TypedCells(dst_cells)));
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

struct MyXWProductOp {
    template<typename LCM, typename RCM, typename CommonInner> static auto invoke() {
        constexpr CellMeta ocm = CellMeta::join(LCM::value, RCM::value).reduce(false);
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        if (std::is_same_v<LCT,double> && std::is_same_v<RCT,double>) {
            assert((std::is_same_v<OCT,double>));
            return my_cblas_double_xw_product_op<CommonInner::value>;
        } else if (std::is_same_v<LCT,float> && std::is_same_v<RCT,float>) {
            assert((std::is_same_v<OCT,float>));
            return my_cblas_float_xw_product_op<CommonInner::value>;
        } else {
            return my_xw_product_op<LCT, RCT, OCT, CommonInner::value>;
        }
    }
};

} // namespace <unnamed>

DenseXWProductFunction::Self::Self(const ValueType &result_type_in,
                                   size_t vector_size_in, size_t result_size_in)
    : result_type(result_type_in),
      vector_size(vector_size_in),
      result_size(result_size_in)
{
}
DenseXWProductFunction::Self::~Self() = default;

DenseXWProductFunction::DenseXWProductFunction(const ValueType &result_type,
                                               const TensorFunction &vector_in,
                                               const TensorFunction &matrix_in,
                                               size_t vector_size,
                                               size_t result_size,
                                               bool common_inner)
    : tensor_function::Op2(result_type, vector_in, matrix_in),
      _vector_size(vector_size),
      _result_size(result_size),
      _common_inner(common_inner)
{
}

InterpretedFunction::Instruction
DenseXWProductFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    Self &self = stash.create<Self>(result_type(), _vector_size, _result_size);
    assert(self.result_type.cell_meta().is_scalar == false);
    using MyTypify = TypifyValue<TypifyCellMeta,vespalib::TypifyBool>;
    auto op = typify_invoke<3,MyTypify,MyXWProductOp>(lhs().result_type().cell_meta().not_scalar(),
                                                      rhs().result_type().cell_meta().not_scalar(),
                                                      _common_inner);
    return InterpretedFunction::Instruction(op, wrap_param<DenseXWProductFunction::Self>(self));
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
DenseXWProductFunction::optimize(const TensorFunction &expr, Stash &stash)
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

} // namespace
