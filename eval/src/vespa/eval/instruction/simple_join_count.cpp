// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_join_count.h"
#include "generic_join.h"
#include <vespa/eval/eval/fast_value.hpp>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

size_t my_intersect_count_fallback(const Value::Index &lhs_idx, const Value::Index &rhs_idx) {
    size_t result = 0.0;
    SparseJoinPlan plan(1);
    SparseJoinState sparse(plan, lhs_idx, rhs_idx);
    auto outer = sparse.first_index.create_view({});
    auto inner = sparse.second_index.create_view(sparse.second_view_dims);
    outer->lookup({});
    while (outer->next_result(sparse.first_address, sparse.first_subspace)) {
        inner->lookup(sparse.address_overlap);
        if (inner->next_result(sparse.second_only_address, sparse.second_subspace)) {
            ++result;
        }
    }
    return result;
}

size_t my_fast_intersect_count(const FastAddrMap *small_map, const FastAddrMap *big_map) {
    size_t result = 0;
    if (big_map->size() < small_map->size()) {
        std::swap(small_map, big_map);
    }
    const auto &labels = small_map->labels();
    for (size_t i = 0; i < labels.size(); ++i) {
        if (big_map->lookup_singledim(labels[i]) != FastAddrMap::npos()) {
            ++result;
        }
    }
    return result;
}

void my_simple_join_count_op(InterpretedFunction::State &state, uint64_t dense_factor) {
    const auto &lhs_idx = state.peek(1).index();
    const auto &rhs_idx = state.peek(0).index();
    double result = dense_factor * (__builtin_expect(are_fast(lhs_idx, rhs_idx), true)
                                    ? my_fast_intersect_count(&as_fast(lhs_idx).map, &as_fast(rhs_idx).map)
                                    : my_intersect_count_fallback(lhs_idx, rhs_idx));
    state.pop_pop_push(state.stash.create<DoubleValue>(result));
}

bool check_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs) {
    return ((res.is_double()) &&
            (lhs.count_mapped_dimensions() == 1) &&
            (lhs.mapped_dimensions() == rhs.mapped_dimensions()));
}

} // namespace <unnamed>

SimpleJoinCount::SimpleJoinCount(const TensorFunction &lhs_in,
                                 const TensorFunction &rhs_in,
                                 uint64_t dense_factor_in)
  : tensor_function::Op2(ValueType::double_type(), lhs_in, rhs_in),
    _dense_factor(dense_factor_in)
{
}

InterpretedFunction::Instruction
SimpleJoinCount::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return InterpretedFunction::Instruction(my_simple_join_count_op, _dense_factor);
}

const TensorFunction &
SimpleJoinCount::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::COUNT)) {
        if (auto join = as<Join>(reduce->child())) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (check_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
                return stash.create<SimpleJoinCount>(lhs, rhs, join->result_type().dense_subspace_size());
            }
        }
    }
    return expr;
}

} // namespace
