// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_merge_function.h"
#include "generic_merge.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename CT, bool single_dim, typename Fun>
const Value& my_fast_sparse_merge(const FastAddrMap &a_map, const FastAddrMap &b_map,
                                  const CT *a_cells, const CT *b_cells,
                                  const MergeParam &params,
                                  Stash &stash)
{
    Fun fun(params.function);
    size_t guess_size = a_map.size() + b_map.size();
    auto &result = stash.create<FastValue<CT,true>>(params.res_type, params.num_mapped_dimensions, 1u, guess_size);
    if constexpr (single_dim) {
        string_id cur_label;
        ConstArrayRef<string_id> addr(&cur_label, 1);
        const auto &a_labels = a_map.labels();
        for (size_t i = 0; i < a_labels.size(); ++i) {
            cur_label = a_labels[i];
            result.add_mapping(addr, cur_label.hash());
            result.my_cells.push_back_fast(a_cells[i]);
        }
        const auto &b_labels = b_map.labels();
        for (size_t i = 0; i < b_labels.size(); ++i) {
            cur_label = b_labels[i];
            auto result_subspace = result.my_index.map.lookup_singledim(cur_label);
            if (result_subspace == FastAddrMap::npos()) {
                result.add_mapping(addr, cur_label.hash());
                result.my_cells.push_back_fast(b_cells[i]);
            } else {
                CT *out_cell = result.my_cells.get(result_subspace);
                out_cell[0] = fun(out_cell[0], b_cells[i]);
            }
        }
    } else {
        a_map.each_map_entry([&](auto lhs_subspace, auto hash)
        {
            result.add_mapping(a_map.get_addr(lhs_subspace), hash);
            result.my_cells.push_back_fast(a_cells[lhs_subspace]);
        });
        b_map.each_map_entry([&](auto rhs_subspace, auto hash)
        {
            auto rhs_addr = b_map.get_addr(rhs_subspace);
            auto result_subspace = result.my_index.map.lookup(rhs_addr, hash);
            if (result_subspace == FastAddrMap::npos()) {
                result.add_mapping(rhs_addr, hash);
                result.my_cells.push_back_fast(b_cells[rhs_subspace]);
            } else {
                CT *out_cell = result.my_cells.get(result_subspace);
                out_cell[0] = fun(out_cell[0], b_cells[rhs_subspace]);
            }
        });
    }
    return result;
}

template <typename CT, bool single_dim, typename Fun>
void my_sparse_merge_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &param = unwrap_param<MergeParam>(param_in);
    assert(param.dense_subspace_size == 1u);
    const Value &a = state.peek(1);
    const Value &b = state.peek(0);
    const auto &a_idx = a.index();
    const auto &b_idx = b.index();
    if (__builtin_expect(are_fast(a_idx, b_idx), true)) {
        auto a_cells = a.cells().typify<CT>();
        auto b_cells = b.cells().typify<CT>();
        const Value &v = my_fast_sparse_merge<CT,single_dim,Fun>(as_fast(a_idx).map, as_fast(b_idx).map,
                                                                 a_cells.cbegin(), b_cells.cbegin(),
                                                                 param, state.stash);
        state.pop_pop_push(v);
    } else {
        auto up = generic_mixed_merge<CT,CT,CT,Fun>(a, b, param);
        state.pop_pop_push(*state.stash.create<std::unique_ptr<Value>>(std::move(up)));
    }
}

struct SelectSparseMergeOp {
    template <typename R1, typename SINGLE_DIM, typename Fun>
    static auto invoke() {
        using CT = CellValueType<R1::value.cell_type>;        
        return my_sparse_merge_op<CT,SINGLE_DIM::value,Fun>;
    }
};

using MyTypify = TypifyValue<TypifyCellMeta,TypifyBool,operation::TypifyOp2>;

} // namespace <unnamed>

SparseMergeFunction::SparseMergeFunction(const tensor_function::Merge &original)
  : tensor_function::Merge(original.result_type(),
                           original.lhs(),
                           original.rhs(),
                           original.function())
{
    assert(compatible_types(result_type(), lhs().result_type(), rhs().result_type()));
}

InterpretedFunction::Instruction
SparseMergeFunction::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    const auto &param = stash.create<MergeParam>(result_type(),
                                                 lhs().result_type(), rhs().result_type(),
                                                 function(), factory);
    size_t num_dims = result_type().count_mapped_dimensions();
    auto op = typify_invoke<3,MyTypify,SelectSparseMergeOp>(result_type().cell_meta().limit(),
                                                            num_dims == 1,
                                                            function());
    return InterpretedFunction::Instruction(op, wrap_param<MergeParam>(param));
}

bool
SparseMergeFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    if ((lhs.cell_type() == rhs.cell_type())
        && (lhs.cell_type() == res.cell_type())
        && (lhs.count_mapped_dimensions() > 0)
        && (lhs.dense_subspace_size() == 1))
    {
        assert(res == lhs);
        assert(res == rhs);
        return true;
    }
    return false;
}

const TensorFunction &
SparseMergeFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto merge = as<Merge>(expr)) {
        const TensorFunction &lhs = merge->lhs();
        const TensorFunction &rhs = merge->rhs();
        if (compatible_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
            return stash.create<SparseMergeFunction>(*merge);
        }
    }
    return expr;
}

} // namespace
