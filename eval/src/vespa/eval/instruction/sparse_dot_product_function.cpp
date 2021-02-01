// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_dot_product_function.h"
#include "generic_join.h"
#include "detect_type.h"
#include <vespa/eval/eval/fast_value.hpp>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename SCT, typename BCT>
double my_fast_sparse_dot_product(const FastValueIndex &small_idx, const FastValueIndex &big_idx,
                                  const SCT *small_cells, const BCT *big_cells)
{
    double result = 0.0;
    small_idx.map.each_map_entry([&](auto small_subspace, auto hash) {
                auto small_addr = small_idx.map.get_addr(small_subspace);
                auto big_subspace = big_idx.map.lookup(small_addr, hash);
                if (big_subspace != FastAddrMap::npos()) {
                    result += (small_cells[small_subspace] * big_cells[big_subspace]);
                }
            });
    return result;
}

template <typename LCT, typename RCT>
void my_sparse_dot_product_op(InterpretedFunction::State &state, uint64_t num_mapped_dims) {
    const auto &lhs_idx = state.peek(1).index();
    const auto &rhs_idx = state.peek(0).index();
    const LCT *lhs_cells = state.peek(1).cells().typify<LCT>().cbegin();
    const RCT *rhs_cells = state.peek(0).cells().typify<RCT>().cbegin();
    if (auto indexes = detect_type<FastValueIndex>(lhs_idx, rhs_idx)) {
        [[likely]];
        const auto &lhs_fast = indexes.get<0>();
        const auto &rhs_fast = indexes.get<1>();
        double result = (rhs_fast.map.size() < lhs_fast.map.size())
                        ? my_fast_sparse_dot_product(rhs_fast, lhs_fast, rhs_cells, lhs_cells)
                        : my_fast_sparse_dot_product(lhs_fast, rhs_fast, lhs_cells, rhs_cells);
        state.pop_pop_push(state.stash.create<ScalarValue<double>>(result));
    } else {
        [[unlikely]];
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
    template <typename LCT, typename RCT>
    static auto invoke() { return my_sparse_dot_product_op<LCT,RCT>; }
};

} // namespace <unnamed>

SparseDotProductFunction::SparseDotProductFunction(const TensorFunction &lhs_in,
                                                   const TensorFunction &rhs_in)
    : tensor_function::Op2(ValueType::make_type(CellType::DOUBLE, {}), lhs_in, rhs_in)
{
}

InterpretedFunction::Instruction
SparseDotProductFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    auto op = typify_invoke<2,TypifyCellType,MyGetFun>(lhs().result_type().cell_type(), rhs().result_type().cell_type());
    return InterpretedFunction::Instruction(op, lhs().result_type().count_mapped_dimensions());
}

bool
SparseDotProductFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    return (res.is_scalar() && (res.cell_type() == CellType::DOUBLE) &&
            lhs.is_sparse() && (rhs.dimensions() == lhs.dimensions()));
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
