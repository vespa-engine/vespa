// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_dot_product_function.h"
#include "generic_join.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename CT, bool single_dim>
double my_fast_sparse_dot_product(const FastValueIndex *small_idx, const FastValueIndex *big_idx,
                                  const CT *small_cells, const CT *big_cells)
{
    double result = 0.0;
    if (big_idx->map.size() < small_idx->map.size()) {
        std::swap(small_idx, big_idx);
        std::swap(small_cells, big_cells);
    }
    if (single_dim) {
        const auto &labels = small_idx->map.labels();
        for (size_t i = 0; i < labels.size(); ++i) {
            auto big_subspace = big_idx->map.lookup_singledim(labels[i]);
            if (big_subspace != FastAddrMap::npos()) {
                result += (small_cells[i] * big_cells[big_subspace]);
            }
        }
    } else {
        small_idx->map.each_map_entry([&](auto small_subspace, auto hash) {
                    auto small_addr = small_idx->map.get_addr(small_subspace);
                    auto big_subspace = big_idx->map.lookup(small_addr, hash);
                    if (big_subspace != FastAddrMap::npos()) {
                        result += (small_cells[small_subspace] * big_cells[big_subspace]);
                    }
                });
    }
    return result;
}

template <typename CT, bool single_dim>
void my_sparse_dot_product_op(InterpretedFunction::State &state, uint64_t num_mapped_dims) {
    const auto &lhs_idx = state.peek(1).index();
    const auto &rhs_idx = state.peek(0).index();
    const CT *lhs_cells = state.peek(1).cells().typify<CT>().cbegin();
    const CT *rhs_cells = state.peek(0).cells().typify<CT>().cbegin();
    if (__builtin_expect(are_fast(lhs_idx, rhs_idx), true)) {
        double result = my_fast_sparse_dot_product<CT,single_dim>(&as_fast(lhs_idx), &as_fast(rhs_idx), lhs_cells, rhs_cells);
        state.pop_pop_push(state.stash.create<ScalarValue<double>>(result));
    } else {
        double result = 0.0;
        SparseJoinPlan plan(num_mapped_dims);
        SparseJoinState sparse(plan, lhs_idx, rhs_idx);
        auto outer = sparse.first_index.create_view({});
        auto inner = sparse.second_index.create_view(sparse.second_view_dims);
        outer->lookup({});
        while (outer->next_result(sparse.first_address, sparse.first_subspace)) {
            inner->lookup(sparse.address_overlap);
            if (inner->next_result(sparse.second_only_address, sparse.second_subspace)) {
                result += (lhs_cells[sparse.lhs_subspace] * rhs_cells[sparse.rhs_subspace]);
            }
        }
        state.pop_pop_push(state.stash.create<ScalarValue<double>>(result));
    }
}

struct MyGetFun {
    template <typename CT, typename SINGLE_DIM>
    static auto invoke() { return my_sparse_dot_product_op<CT,SINGLE_DIM::value>; }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyBool>;

} // namespace <unnamed>

SparseDotProductFunction::SparseDotProductFunction(const TensorFunction &lhs_in,
                                                   const TensorFunction &rhs_in)
    : tensor_function::Op2(ValueType::make_type(CellType::DOUBLE, {}), lhs_in, rhs_in)
{
}

InterpretedFunction::Instruction
SparseDotProductFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    size_t num_dims = lhs().result_type().count_mapped_dimensions();
    auto op = typify_invoke<2,MyTypify,MyGetFun>(lhs().result_type().cell_type(),
                                                 (num_dims == 1));
    return InterpretedFunction::Instruction(op, num_dims);
}

bool
SparseDotProductFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    return (res.is_scalar() && (res.cell_type() == CellType::DOUBLE) &&
            lhs.is_sparse() && (rhs.dimensions() == lhs.dimensions()) &&
            lhs.cell_type() == rhs.cell_type());
}

const TensorFunction &
SparseDotProductFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (compatible_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
                return stash.create<SparseDotProductFunction>(lhs, rhs);
            }
        }
    }
    return expr;
}

} // namespace
