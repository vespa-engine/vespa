// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mapped_lookup.h"
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/require.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename CT>
ConstArrayRef<CT> my_mapped_lookup_fallback(const Value::Index &key_idx, const Value::Index &map_idx,
                                            const CT *key_cells, const CT *map_cells, size_t res_size, Stash &stash) __attribute__((noinline));
template <typename CT>
ConstArrayRef<CT> my_mapped_lookup_fallback(const Value::Index &key_idx, const Value::Index &map_idx,
                                            const CT *key_cells, const CT * map_cells, size_t res_size, Stash &stash)
{
    SparseJoinPlan plan(1);
    auto result = stash.create_array<CT>(res_size);
    SparseJoinState sparse(plan, key_idx, map_idx);
    auto outer = sparse.first_index.create_view({});
    auto inner = sparse.second_index.create_view(sparse.second_view_dims);
    outer->lookup({});
    while (outer->next_result(sparse.first_address, sparse.first_subspace)) {
        inner->lookup(sparse.address_overlap);
        if (inner->next_result(sparse.second_only_address, sparse.second_subspace)) {
            auto factor = key_cells[sparse.lhs_subspace];
            const CT *match = map_cells + (res_size * sparse.rhs_subspace);
            for (size_t i = 0; i < result.size(); ++i) {
                result[i] += factor * match[i];
            }
        }
    }
    return result;
}

template <typename CT>
struct MappedLookupResult {
    ArrayRef<CT> value;
    MappedLookupResult(size_t res_size, Stash &stash)
      : value(stash.create_array<CT>(res_size)) {}
    void process_match(CT factor, const CT *match) {
        for (size_t i = 0; i < value.size(); ++i) {
            value[i] += factor * match[i];
        }
    }
};

template <typename CT>
ConstArrayRef<CT> my_fast_mapped_lookup(const FastAddrMap &key_map, const FastAddrMap &map_map,
                                        const CT *key_cells, const CT *map_cells, size_t res_size, Stash &stash)
{
    if ((key_map.size() == 1) && (key_cells[0] == 1.0)) {
        auto subspace = map_map.lookup_singledim(key_map.labels()[0]);
        if (subspace != FastAddrMap::npos()) {
            return {map_cells + (res_size * subspace), res_size};
        } else {
            return stash.create_array<CT>(res_size);
        }
    }
    MappedLookupResult<CT> result(res_size, stash);
    if (key_map.size() <= map_map.size()) {
        const auto &labels = key_map.labels();
        for (size_t i = 0; i < labels.size(); ++i) {
            auto subspace = map_map.lookup_singledim(labels[i]);
            if (subspace != FastAddrMap::npos()) {
                result.process_match(key_cells[i], map_cells + (res_size * subspace));
            }
        }
    } else {
        const auto &labels = map_map.labels();
        for (size_t i = 0; i < labels.size(); ++i) {
            auto subspace = key_map.lookup_singledim(labels[i]);
            if (subspace != FastAddrMap::npos()) {
                result.process_match(key_cells[subspace], map_cells + (res_size * i));
            }
        }
    }
    return result.value;
}

template <typename CT>
void my_mapped_lookup_op(InterpretedFunction::State &state, uint64_t param) {
    const auto &res_type = unwrap_param<ValueType>(param);
    const auto &key_idx = state.peek(1).index();
    const auto &map_idx = state.peek(0).index();
    const CT *key_cells = state.peek(1).cells().typify<CT>().cbegin();
    const CT *map_cells = state.peek(0).cells().typify<CT>().cbegin();
    auto result = __builtin_expect(are_fast(key_idx, map_idx), true)
        ? my_fast_mapped_lookup<CT>(as_fast(key_idx).map, as_fast(map_idx).map, key_cells, map_cells, res_type.dense_subspace_size(), state.stash)
        : my_mapped_lookup_fallback<CT>(key_idx, map_idx, key_cells, map_cells, res_type.dense_subspace_size(), state.stash);
    state.pop_pop_push(state.stash.create<DenseValueView>(res_type, TypedCells(result)));
}

bool check_types(const ValueType &res, const ValueType &key, const ValueType &map) {
    return ((res.is_dense()) && (key.dense_subspace_size() == 1) && (map.is_mixed()) &&
            (res.cell_type() == key.cell_type()) &&
            (res.cell_type() == map.cell_type()) &&
            ((res.cell_type() == CellType::FLOAT) || (res.cell_type() == CellType::DOUBLE)) &&
            (key.mapped_dimensions().size() == 1) &&
            (key.mapped_dimensions() == map.mapped_dimensions()) &&
            (map.nontrivial_indexed_dimensions() == res.nontrivial_indexed_dimensions()));
}

} // namespace <unnamed>

MappedLookup::MappedLookup(const ValueType &res_type,
                           const TensorFunction &key_in,
                           const TensorFunction &map_in)
  : tensor_function::Op2(res_type, key_in, map_in)
{
}

InterpretedFunction::Instruction
MappedLookup::compile_self(const ValueBuilderFactory &, Stash &) const
{
    uint64_t param = wrap_param<ValueType>(result_type());
    if (result_type().cell_type() == CellType::FLOAT) {
        return {my_mapped_lookup_op<float>, param};
    }
    if (result_type().cell_type() == CellType::DOUBLE) {
        return {my_mapped_lookup_op<double>, param};
    }
    REQUIRE_FAILED("cell types must be float or double");
}

const TensorFunction &
MappedLookup::optimize(const TensorFunction &expr, Stash &stash)
{
    auto reduce = as<Reduce>(expr);
    if (reduce && (reduce->aggr() == Aggr::SUM)) {
        auto join = as<Join>(reduce->child());
        if (join && (join->function() == Mul::f)) {
            const TensorFunction &lhs = join->lhs();
            const TensorFunction &rhs = join->rhs();
            if (check_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
                return stash.create<MappedLookup>(expr.result_type(), lhs, rhs);
            }
            if (check_types(expr.result_type(), rhs.result_type(), lhs.result_type())) {
                return stash.create<MappedLookup>(expr.result_type(), rhs, lhs);
            }
        }
    }
    return expr;
}

} // namespace
