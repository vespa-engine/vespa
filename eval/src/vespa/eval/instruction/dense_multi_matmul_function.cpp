// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_multi_matmul_function.h"
#include <vespa/vespalib/objects/objectvisitor.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/operation.h>
#include <cassert>
#include <cblas.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;

using Dim = ValueType::Dimension;
using DimList = std::vector<Dim>;

namespace {

void my_cblas_double_multi_matmul_op(InterpretedFunction::State &state, uint64_t param) {
    using CT = double;
    const DenseMultiMatMulFunction &self = unwrap_param<DenseMultiMatMulFunction>(param);
    size_t lhs_block_size = self.lhs_size() * self.common_size();
    size_t rhs_block_size = self.rhs_size() * self.common_size();
    size_t dst_block_size = self.lhs_size() * self.rhs_size();
    size_t num_blocks = self.matmul_cnt();
    const CT *lhs = state.peek(1).cells().typify<CT>().cbegin();
    const CT *rhs = state.peek(0).cells().typify<CT>().cbegin();
    auto dst_cells = state.stash.create_array<CT>(dst_block_size * num_blocks);
    CT *dst = dst_cells.begin();
    for (size_t i = 0; i < num_blocks; ++i, lhs += lhs_block_size, rhs += rhs_block_size, dst += dst_block_size) {
        cblas_dgemm(CblasRowMajor, self.lhs_common_inner() ? CblasNoTrans : CblasTrans, self.rhs_common_inner() ? CblasTrans : CblasNoTrans,
                    self.lhs_size(), self.rhs_size(), self.common_size(), 1.0,
                    lhs, self.lhs_common_inner() ? self.common_size() : self.lhs_size(),
                    rhs, self.rhs_common_inner() ? self.common_size() : self.rhs_size(),
                    0.0, dst, self.rhs_size());
    }
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type(), TypedCells(dst_cells)));
}

void my_cblas_float_multi_matmul_op(InterpretedFunction::State &state, uint64_t param) {
    using CT = float;
    const DenseMultiMatMulFunction &self = unwrap_param<DenseMultiMatMulFunction>(param);
    size_t lhs_block_size = self.lhs_size() * self.common_size();
    size_t rhs_block_size = self.rhs_size() * self.common_size();
    size_t dst_block_size = self.lhs_size() * self.rhs_size();
    size_t num_blocks = self.matmul_cnt();
    const CT *lhs = state.peek(1).cells().typify<CT>().cbegin();
    const CT *rhs = state.peek(0).cells().typify<CT>().cbegin();
    auto dst_cells = state.stash.create_array<CT>(dst_block_size * num_blocks);
    CT *dst = dst_cells.begin();
    for (size_t i = 0; i < num_blocks; ++i, lhs += lhs_block_size, rhs += rhs_block_size, dst += dst_block_size) {
        cblas_sgemm(CblasRowMajor, self.lhs_common_inner() ? CblasNoTrans : CblasTrans, self.rhs_common_inner() ? CblasTrans : CblasNoTrans,
                    self.lhs_size(), self.rhs_size(), self.common_size(), 1.0,
                    lhs, self.lhs_common_inner() ? self.common_size() : self.lhs_size(),
                    rhs, self.rhs_common_inner() ? self.common_size() : self.rhs_size(),
                    0.0, dst, self.rhs_size());
    }
    state.pop_pop_push(state.stash.create<DenseValueView>(self.result_type(), TypedCells(dst_cells)));
}

InterpretedFunction::op_function my_select(CellType cell_type) {
    if (cell_type == CellType::DOUBLE) {
        return my_cblas_double_multi_matmul_op;
    }
    if (cell_type == CellType::FLOAT) {
        return my_cblas_float_multi_matmul_op;
    }
    abort();
}

struct CommonDim {
    bool valid;
    bool inner;
    CommonDim(const DimList &list, const vespalib::string &dim)
        : valid(true), inner(false)
    {
        if (list[list.size() - 1].name == dim) {
            inner = true;
        } else if (list[list.size() - 2].name != dim) {
            valid = false;
        }
    }
    const Dim &get(const DimList &dims) const {
        return dims[dims.size() - (inner ? 1 : 2)];
    }
    const Dim &inv(const DimList &dims) const {
        return dims[dims.size() - (inner ? 2 : 1)];
    }
};

// Currently, non-matmul dimensions are required to be identical
// (after trivial dimensions are ignored). This restriction is added
// to reduce complexity and might be removed in the future if/when a
// relevant use-case arises.
struct DimPrefix {
    bool valid;
    size_t size;
    DimPrefix(const DimList &a, const DimList &b)
        : valid(true), size(1)
    {
        if (a.size() == b.size()) {
            for (size_t i = 0; i < (a.size() - 2); ++i) {
                if (a[i] == b[i]) {
                    size *= a[i].size;
                } else {
                    valid = false;
                }
            }
        } else {
            valid = false;
        }
    }
};

bool check_input_type(const ValueType &type, const DimList &relevant) {
    return (type.is_dense() &&
            (relevant.size() >= 2) &&
            ((type.cell_type() == CellType::FLOAT) || (type.cell_type() == CellType::DOUBLE)));
}

bool is_multi_matmul(const ValueType &a, const ValueType &b, const vespalib::string &reduce_dim) {
    auto dims_a = a.nontrivial_indexed_dimensions();
    auto dims_b = b.nontrivial_indexed_dimensions();
    if (check_input_type(a, dims_a) && check_input_type(b, dims_b) && (a.cell_type() == b.cell_type())) {
        CommonDim cd_a(dims_a, reduce_dim);
        CommonDim cd_b(dims_b, reduce_dim);
        DimPrefix prefix(dims_a, dims_b);
        return (cd_a.valid && cd_b.valid && prefix.valid &&
                (b.dimension_index(cd_a.inv(dims_a).name) == Dim::npos) &&
                (a.dimension_index(cd_b.inv(dims_b).name) == Dim::npos));
    }
    return false;
}

const TensorFunction &create_multi_matmul(const TensorFunction &a, const TensorFunction &b,
                                          const vespalib::string &reduce_dim, const ValueType &result_type, Stash &stash)
{
    auto dims_a = a.result_type().nontrivial_indexed_dimensions();
    auto dims_b = b.result_type().nontrivial_indexed_dimensions();
    CommonDim cd_a(dims_a, reduce_dim);
    CommonDim cd_b(dims_b, reduce_dim);
    DimPrefix prefix(dims_a, dims_b);
    size_t a_size = cd_a.inv(dims_a).size;
    size_t b_size = cd_b.inv(dims_b).size;
    size_t common_size = cd_a.get(dims_a).size;
    bool a_is_lhs = (cd_a.inv(dims_a).name < cd_b.inv(dims_b).name);
    if (a_is_lhs) {
        return stash.create<DenseMultiMatMulFunction>(result_type, a, b, a_size, common_size, b_size, prefix.size, cd_a.inner, cd_b.inner);
    } else {
        return stash.create<DenseMultiMatMulFunction>(result_type, b, a, b_size, common_size, a_size, prefix.size, cd_b.inner, cd_a.inner);
    }
}

} // namespace <unnamed>

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
DenseMultiMatMulFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = my_select(lhs().result_type().cell_type());
    return InterpretedFunction::Instruction(op, wrap_param<DenseMultiMatMulFunction>(*this));
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

} // namespace
