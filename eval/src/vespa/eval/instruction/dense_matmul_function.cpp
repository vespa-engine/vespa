// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_matmul_function.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <cassert>
#include <cblas.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

namespace {

template <typename LCT, typename RCT, typename OCT, bool lhs_common_inner, bool rhs_common_inner>
OCT my_dot_product(const LCT *lhs, const RCT *rhs, size_t lhs_size, size_t common_size, size_t rhs_size) {
    OCT result = 0.0;
    for (size_t i = 0; i < common_size; ++i) {
        result += ((*lhs) * (*rhs));
        lhs += (lhs_common_inner ? 1 : lhs_size);
        rhs += (rhs_common_inner ? 1 : rhs_size);
    }
    return result;
}

template <typename LCT, typename RCT, typename OCT, bool lhs_common_inner, bool rhs_common_inner>
void my_matmul_op(InterpretedFunction::State &state, uint64_t param) {
    const DenseMatMulFunction::Self &self = unwrap_param<DenseMatMulFunction::Self>(param);
    auto lhs_cells = state.peek(1).cells().typify<LCT>();
    auto rhs_cells = state.peek(0).cells().typify<RCT>();
    auto dst_cells = state.stash.create_uninitialized_array<OCT>(self.lhs_size * self.rhs_size);
    OCT *dst = dst_cells.begin();
    const LCT *lhs = lhs_cells.cbegin();
    for (size_t i = 0; i < self.lhs_size; ++i) {
        const RCT *rhs = rhs_cells.cbegin();
        for (size_t j = 0; j < self.rhs_size; ++j) {
            *dst++ = my_dot_product<LCT,RCT,OCT,lhs_common_inner,rhs_common_inner>(lhs, rhs,
                                                                                   self.lhs_size, self.common_size, self.rhs_size);
            rhs += (rhs_common_inner ? self.common_size : 1);
        }
        lhs += (lhs_common_inner ? self.common_size : 1);
    }
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type, TypedCells(dst_cells)));
}

template <bool lhs_common_inner, bool rhs_common_inner>
void my_cblas_double_matmul_op(InterpretedFunction::State &state, uint64_t param) {
    const DenseMatMulFunction::Self &self = unwrap_param<DenseMatMulFunction::Self>(param);
    auto lhs_cells = state.peek(1).cells().typify<double>();
    auto rhs_cells = state.peek(0).cells().typify<double>();
    auto dst_cells = state.stash.create_array<double>(self.lhs_size * self.rhs_size);
    cblas_dgemm(CblasRowMajor, lhs_common_inner ? CblasNoTrans : CblasTrans, rhs_common_inner ? CblasTrans : CblasNoTrans,
                self.lhs_size, self.rhs_size, self.common_size, 1.0,
                lhs_cells.cbegin(), lhs_common_inner ? self.common_size : self.lhs_size,
                rhs_cells.cbegin(), rhs_common_inner ? self.common_size : self.rhs_size,
                0.0, dst_cells.begin(), self.rhs_size);
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type, TypedCells(dst_cells)));
}

template <bool lhs_common_inner, bool rhs_common_inner>
void my_cblas_float_matmul_op(InterpretedFunction::State &state, uint64_t param) {
    const DenseMatMulFunction::Self &self = unwrap_param<DenseMatMulFunction::Self>(param);
    auto lhs_cells = state.peek(1).cells().typify<float>();
    auto rhs_cells = state.peek(0).cells().typify<float>();
    auto dst_cells = state.stash.create_array<float>(self.lhs_size * self.rhs_size);
    cblas_sgemm(CblasRowMajor, lhs_common_inner ? CblasNoTrans : CblasTrans, rhs_common_inner ? CblasTrans : CblasNoTrans,
                self.lhs_size, self.rhs_size, self.common_size, 1.0,
                lhs_cells.cbegin(), lhs_common_inner ? self.common_size : self.lhs_size,
                rhs_cells.cbegin(), rhs_common_inner ? self.common_size : self.rhs_size,
                0.0, dst_cells.begin(), self.rhs_size);
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type, TypedCells(dst_cells)));
}

bool is_matrix(const ValueType &type) {
    return (type.is_dense() && (type.dimensions().size() == 2));
}

bool is_matmul(const ValueType &a, const ValueType &b,
               const vespalib::string &reduce_dim, const ValueType &result_type)
{
    size_t npos = ValueType::Dimension::npos;
    return (is_matrix(a) && is_matrix(b) && is_matrix(result_type) &&
            (a.dimension_index(reduce_dim) != npos) &&
            (b.dimension_index(reduce_dim) != npos));
}

const ValueType::Dimension &dim(const TensorFunction &expr, size_t idx) {
    return expr.result_type().dimensions()[idx];
}

size_t inv(size_t idx) { return (1 - idx); }

const TensorFunction &create_matmul(const TensorFunction &a, const TensorFunction &b,
                                    const vespalib::string &reduce_dim, const ValueType &result_type, Stash &stash) {
    size_t a_idx = a.result_type().dimension_index(reduce_dim);
    size_t b_idx = b.result_type().dimension_index(reduce_dim);
    assert(a_idx != ValueType::Dimension::npos);
    assert(b_idx != ValueType::Dimension::npos);
    assert(dim(a, a_idx).size == dim(b, b_idx).size);
    bool a_common_inner = (a_idx == 1);
    bool b_common_inner = (b_idx == 1);
    size_t a_size = dim(a, inv(a_idx)).size;
    size_t b_size = dim(b, inv(b_idx)).size;
    size_t common_size = dim(a, a_idx).size;
    bool a_is_lhs = (dim(a, inv(a_idx)).name < dim(b, inv(b_idx)).name);
    if (a_is_lhs) {
        return stash.create<DenseMatMulFunction>(result_type, a, b, a_size, common_size, b_size, a_common_inner, b_common_inner);
    } else {
        return stash.create<DenseMatMulFunction>(result_type, b, a, b_size, common_size, a_size, b_common_inner, a_common_inner);
    }
}

struct SelectDenseMatmul {
    template<typename LCM, typename RCM, typename LhsCommonInner, typename RhsCommonInner> static auto invoke() {
        constexpr CellMeta ocm = CellMeta::join(LCM::value, RCM::value).reduce(false);
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        using OCT = CellValueType<ocm.cell_type>;
        if (std::is_same_v<LCT,double> && std::is_same_v<RCT,double>) {
            return my_cblas_double_matmul_op<LhsCommonInner::value, RhsCommonInner::value>;
        } else if (std::is_same_v<LCT,float> && std::is_same_v<RCT,float>) {
            return my_cblas_float_matmul_op<LhsCommonInner::value, RhsCommonInner::value>;
        } else {
            return my_matmul_op<LCT, RCT, OCT, LhsCommonInner::value, RhsCommonInner::value>;
        }
    }
};

} // namespace <unnamed>

DenseMatMulFunction::Self::Self(const ValueType &result_type_in,
                                size_t lhs_size_in,
                                size_t common_size_in,
                                size_t rhs_size_in)
    : result_type(result_type_in),
      lhs_size(lhs_size_in),
      common_size(common_size_in),
      rhs_size(rhs_size_in)
{
}

DenseMatMulFunction::Self::~Self() = default;

DenseMatMulFunction::DenseMatMulFunction(const ValueType &result_type,
                                         const TensorFunction &lhs_in,
                                         const TensorFunction &rhs_in,
                                         size_t lhs_size,
                                         size_t common_size,
                                         size_t rhs_size,
                                         bool lhs_common_inner,
                                         bool rhs_common_inner)
    : Super(result_type, lhs_in, rhs_in),
      _lhs_size(lhs_size),
      _common_size(common_size),
      _rhs_size(rhs_size),
      _lhs_common_inner(lhs_common_inner),
      _rhs_common_inner(rhs_common_inner)
{
}

DenseMatMulFunction::~DenseMatMulFunction() = default;

InterpretedFunction::Instruction
DenseMatMulFunction::compile_self(const ValueBuilderFactory &, Stash &stash) const
{
    using MyTypify = TypifyValue<TypifyCellMeta,TypifyBool>;
    Self &self = stash.create<Self>(result_type(), _lhs_size, _common_size, _rhs_size);
    auto op = typify_invoke<4,MyTypify,SelectDenseMatmul>(
        lhs().result_type().cell_meta().not_scalar(),
        rhs().result_type().cell_meta().not_scalar(),
        _lhs_common_inner, _rhs_common_inner);
    return InterpretedFunction::Instruction(op, wrap_param<DenseMatMulFunction::Self>(self));
}

void
DenseMatMulFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitInt("lhs_size", _lhs_size);
    visitor.visitInt("common_size", _common_size);
    visitor.visitInt("rhs_size", _rhs_size);
    visitor.visitBool("lhs_common_inner", _lhs_common_inner);
    visitor.visitBool("rhs_common_inner", _rhs_common_inner);
}

const TensorFunction &
DenseMatMulFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM) && (reduce->dimensions().size() == 1)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &a = join->lhs();
            const TensorFunction &b = join->rhs();
            if (is_matmul(a.result_type(), b.result_type(), reduce->dimensions()[0], expr.result_type())) {
                return create_matmul(a, b, reduce->dimensions()[0], expr.result_type(), stash);
            }
        }
    }
    return expr;
}

} // namespace
