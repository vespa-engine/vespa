// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_multi_matmul_function.h"
#include "dense_tensor_view.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <cassert>

#include <cblas.h>

namespace vespalib::tensor {

using eval::ValueType;
using eval::TensorFunction;
using eval::InterpretedFunction;
using eval::TensorEngine;
using eval::as;
using eval::Aggr;
using namespace eval::tensor_function;
using namespace eval::operation;

namespace {

void my_cblas_double_multi_matmul_op(InterpretedFunction::State &state, uint64_t param) {
    using CT = double;
    const DenseMultiMatMulFunction &self = *((const DenseMultiMatMulFunction *)(param));
    size_t lhs_block_size = self.lhs_size() * self.common_size();
    size_t rhs_block_size = self.rhs_size() * self.common_size();
    size_t dst_block_size = self.lhs_size() * self.rhs_size();
    size_t num_blocks = self.matmul_cnt();
    const CT *lhs = DenseTensorView::typify_cells<CT>(state.peek(1)).cbegin();
    const CT *rhs = DenseTensorView::typify_cells<CT>(state.peek(0)).cbegin();
    auto dst_cells = state.stash.create_array<CT>(dst_block_size * num_blocks);
    CT *dst = dst_cells.begin();
    for (size_t i = 0; i < num_blocks; ++i, lhs += lhs_block_size, rhs += rhs_block_size, dst += dst_block_size) {
        cblas_dgemm(CblasRowMajor, self.lhs_common_inner() ? CblasNoTrans : CblasTrans, self.rhs_common_inner() ? CblasTrans : CblasNoTrans,
                    self.lhs_size(), self.rhs_size(), self.common_size(), 1.0,
                    lhs, self.lhs_common_inner() ? self.common_size() : self.lhs_size(),
                    rhs, self.rhs_common_inner() ? self.common_size() : self.rhs_size(),
                    0.0, dst, self.rhs_size());
    }
    state.pop_pop_push(state.stash.create<DenseTensorView>(self.result_type(), TypedCells(dst_cells)));
}

void my_cblas_float_multi_matmul_op(InterpretedFunction::State &state, uint64_t param) {
    using CT = float;
    const DenseMultiMatMulFunction &self = *((const DenseMultiMatMulFunction *)(param));
    size_t lhs_block_size = self.lhs_size() * self.common_size();
    size_t rhs_block_size = self.rhs_size() * self.common_size();
    size_t dst_block_size = self.lhs_size() * self.rhs_size();
    size_t num_blocks = self.matmul_cnt();
    const CT *lhs = DenseTensorView::typify_cells<CT>(state.peek(1)).cbegin();
    const CT *rhs = DenseTensorView::typify_cells<CT>(state.peek(0)).cbegin();
    auto dst_cells = state.stash.create_array<CT>(dst_block_size * num_blocks);
    CT *dst = dst_cells.begin();
    for (size_t i = 0; i < num_blocks; ++i, lhs += lhs_block_size, rhs += rhs_block_size, dst += dst_block_size) {
        cblas_sgemm(CblasRowMajor, self.lhs_common_inner() ? CblasNoTrans : CblasTrans, self.rhs_common_inner() ? CblasTrans : CblasNoTrans,
                    self.lhs_size(), self.rhs_size(), self.common_size(), 1.0,
                    lhs, self.lhs_common_inner() ? self.common_size() : self.lhs_size(),
                    rhs, self.rhs_common_inner() ? self.common_size() : self.rhs_size(),
                    0.0, dst, self.rhs_size());
    }
    state.pop_pop_push(state.stash.create<DenseTensorView>(self.result_type(), TypedCells(dst_cells)));
}

InterpretedFunction::op_function my_select(CellType cell_type) {
    if (cell_type == ValueType::CellType::DOUBLE) {
        return my_cblas_double_multi_matmul_op;
    }
    if (cell_type == ValueType::CellType::FLOAT) {
        return my_cblas_float_multi_matmul_op;
    }
    abort();
}

struct CommonDim {
    bool valid;
    bool inner;
    CommonDim(const ValueType &type, const vespalib::string &dim)
        : valid(true), inner(false)
    {
        size_t size = type.dimensions().size();
        if (type.dimensions()[size - 1].name == dim) {
            inner = true;
        } else if (type.dimensions()[size - 2].name != dim) {
            valid = false;
        }
    }
    const ValueType::Dimension &get(const ValueType &type) const {
        size_t size = type.dimensions().size();
        return type.dimensions()[size - (inner ? 1 : 2)];
    }
    const ValueType::Dimension &get(const TensorFunction &expr) const {
        return get(expr.result_type());
    }
    const ValueType::Dimension &inv(const ValueType &type) const {
        size_t size = type.dimensions().size();
        return type.dimensions()[size - (inner ? 2 : 1)];
    }
    const ValueType::Dimension &inv(const TensorFunction &expr) const {
        return inv(expr.result_type());
    }
};

// Currently, non-matmul dimensions are required to be identical. This
// restriction is added to reduce complexity and might be removed in
// the future if/when a relevant use-case arises.
struct DimPrefix {
    bool valid;
    size_t size;
    DimPrefix(const ValueType &a, const ValueType &b)
        : valid(true), size(1)
    {
        if (a.dimensions().size() == b.dimensions().size()) {
            for (size_t i = 0; i < (a.dimensions().size() - 2); ++i) {
                if (a.dimensions()[i] == b.dimensions()[i]) {
                    size *= a.dimensions()[i].size;
                } else {
                    valid = false;
                }
            }
        } else {
            valid = false;
        }
    }
};

bool check_input_type(const ValueType &type) {
    return (type.is_dense() &&
            (type.dimensions().size() >= 2) &&
            ((type.cell_type() == CellType::FLOAT) || (type.cell_type() == CellType::DOUBLE)));
}

bool is_multi_matmul(const ValueType &a, const ValueType &b, const vespalib::string &reduce_dim) {
    if (check_input_type(a) && check_input_type(b) && (a.cell_type() == b.cell_type())) {
        CommonDim cd_a(a, reduce_dim);
        CommonDim cd_b(b, reduce_dim);
        DimPrefix prefix(a, b);
        return (cd_a.valid && cd_b.valid && prefix.valid &&
                (b.dimension_index(cd_a.inv(a).name) == ValueType::Dimension::npos) &&
                (a.dimension_index(cd_b.inv(b).name) == ValueType::Dimension::npos));
    }
    return false;
}

const TensorFunction &create_multi_matmul(const TensorFunction &a, const TensorFunction &b,
                                          const vespalib::string &reduce_dim, const ValueType &result_type, Stash &stash)
{
    CommonDim cd_a(a.result_type(), reduce_dim);
    CommonDim cd_b(b.result_type(), reduce_dim);
    DimPrefix prefix(a.result_type(), b.result_type());
    size_t a_size = cd_a.inv(a).size;
    size_t b_size = cd_b.inv(b).size;
    size_t common_size = cd_a.get(a).size;
    bool a_is_lhs = (cd_a.inv(a).name < cd_b.inv(b).name);
    if (a_is_lhs) {
        return stash.create<DenseMultiMatMulFunction>(result_type, a, b, a_size, common_size, b_size, prefix.size, cd_a.inner, cd_b.inner);
    } else {
        return stash.create<DenseMultiMatMulFunction>(result_type, b, a, b_size, common_size, a_size, prefix.size, cd_b.inner, cd_a.inner);
    }
}

} // namespace vespalib::tensor::<unnamed>

DenseMultiMatMulFunction::DenseMultiMatMulFunction(const ValueType &result_type,
                                                   const TensorFunction &lhs_in,
                                                   const TensorFunction &rhs_in,
                                                   size_t lhs_size,
                                                   size_t common_size,
                                                   size_t rhs_size,
                                                   size_t matmul_cnt,
                                                   bool lhs_common_inner,
                                                   bool rhs_common_inner)
    : Super(result_type, lhs_in, rhs_in),
      _lhs_size(lhs_size),
      _common_size(common_size),
      _rhs_size(rhs_size),
      _matmul_cnt(matmul_cnt),
      _lhs_common_inner(lhs_common_inner),
      _rhs_common_inner(rhs_common_inner)
{
}

DenseMultiMatMulFunction::~DenseMultiMatMulFunction() = default;

InterpretedFunction::Instruction
DenseMultiMatMulFunction::compile_self(const TensorEngine &, Stash &) const
{
    auto op = my_select(lhs().result_type().cell_type());
    return InterpretedFunction::Instruction(op, (uint64_t)(this));
}

void
DenseMultiMatMulFunction::visit_self(vespalib::ObjectVisitor &visitor) const
{
    Super::visit_self(visitor);
    visitor.visitInt("lhs_size", _lhs_size);
    visitor.visitInt("common_size", _common_size);
    visitor.visitInt("rhs_size", _rhs_size);
    visitor.visitInt("matmul_cnt", _matmul_cnt);
    visitor.visitBool("lhs_common_inner", _lhs_common_inner);
    visitor.visitBool("rhs_common_inner", _rhs_common_inner);
}

const TensorFunction &
DenseMultiMatMulFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM) && (reduce->dimensions().size() == 1)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &a = join->lhs();
            const TensorFunction &b = join->rhs();
            if (is_multi_matmul(a.result_type(), b.result_type(), reduce->dimensions()[0])) {
                return create_multi_matmul(a, b, reduce->dimensions()[0], expr.result_type(), stash);
            }
        }
    }
    return expr;
}

} // namespace vespalib::tensor
