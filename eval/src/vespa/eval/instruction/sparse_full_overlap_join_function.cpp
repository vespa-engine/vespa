// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_full_overlap_join_function.h"
#include "generic_join.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename CT, typename Fun, bool single_dim>
const Value &my_fast_sparse_full_overlap_join(const FastAddrMap &lhs_map, const FastAddrMap &rhs_map,
                                              const CT *lhs_cells, const CT *rhs_cells,
                                              const JoinParam &param, Stash &stash)
{
    Fun fun(param.function);
    auto &result = stash.create<FastValue<CT,true>>(param.res_type, lhs_map.addr_size(), 1, lhs_map.size());
    if constexpr (single_dim) {
        const auto &labels = lhs_map.labels();
        for (size_t i = 0; i < labels.size(); ++i) {
            auto rhs_subspace = rhs_map.lookup_singledim(labels[i]);
            if (rhs_subspace != FastAddrMap::npos()) {
                result.add_singledim_mapping(labels[i]);
                auto cell_value = fun(lhs_cells[i], rhs_cells[rhs_subspace]);
                result.my_cells.push_back_fast(cell_value);
            }
        }
    } else {
        lhs_map.each_map_entry([&](auto lhs_subspace, auto hash) {
                    auto lhs_addr = lhs_map.get_addr(lhs_subspace);
                    auto rhs_subspace = rhs_map.lookup(lhs_addr, hash);
                    if (rhs_subspace != FastAddrMap::npos()) {
                        result.add_mapping(lhs_addr, hash);
                        auto cell_value = fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]);
                        result.my_cells.push_back_fast(cell_value);
                    }
                });
    }
    return result;
}

template <typename CT, typename Fun, bool single_dim>
const Value &my_fast_sparse_full_overlap_join_dispatch(const FastAddrMap &lhs_map, const FastAddrMap &rhs_map,
                                                       const CT *lhs_cells, const CT *rhs_cells,
                                                       const JoinParam &param, Stash &stash)
{
    return (rhs_map.size() < lhs_map.size())
        ? my_fast_sparse_full_overlap_join<CT,SwapArgs2<Fun>,single_dim>(rhs_map, lhs_map, rhs_cells, lhs_cells, param, stash)
        : my_fast_sparse_full_overlap_join<CT,Fun,single_dim>(lhs_map, rhs_map, lhs_cells, rhs_cells, param, stash);
}

template <typename CT, typename Fun, bool single_dim>
void my_sparse_full_overlap_join_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &param = unwrap_param<JoinParam>(param_in);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    const auto &lhs_idx = lhs.index();
    const auto &rhs_idx = rhs.index();
    if (__builtin_expect(are_fast(lhs_idx, rhs_idx), true)) {
        const Value &res = my_fast_sparse_full_overlap_join_dispatch<CT,Fun,single_dim>(as_fast(lhs_idx).map, as_fast(rhs_idx).map,
                lhs.cells().typify<CT>().cbegin(), rhs.cells().typify<CT>().cbegin(), param, state.stash);
        state.pop_pop_push(res);
    } else {
        auto res = generic_mixed_join<CT,CT,CT,Fun>(lhs, rhs, param);
        state.pop_pop_push(*state.stash.create<std::unique_ptr<Value>>(std::move(res)));
    }
}

struct SelectSparseFullOverlapJoinOp {
    template <typename R1, typename Fun, typename SINGLE_DIM>
    static auto invoke() {
        using CT = CellValueType<R1::value.cell_type>;
        return my_sparse_full_overlap_join_op<CT,Fun,SINGLE_DIM::value>;
    }
};

using MyTypify = TypifyValue<TypifyCellMeta,operation::TypifyOp2,TypifyBool>;

bool is_sparse_like(const ValueType &type) {
    return ((type.count_mapped_dimensions() > 0) && (type.dense_subspace_size() == 1));
}

} // namespace <unnamed>

SparseFullOverlapJoinFunction::SparseFullOverlapJoinFunction(const tensor_function::Join &original)
    : tensor_function::Join(original.result_type(),
                            original.lhs(),
                            original.rhs(),
                            original.function())
{
    assert(compatible_types(result_type(), lhs().result_type(), rhs().result_type()));
}

InterpretedFunction::Instruction
SparseFullOverlapJoinFunction::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    const auto &param = stash.create<JoinParam>(result_type(), lhs().result_type(), rhs().result_type(), function(), factory);
    assert(result_type() == ValueType::join(lhs().result_type(), rhs().result_type()));
    bool single_dim = (result_type().count_mapped_dimensions() == 1);
    auto op = typify_invoke<3,MyTypify,SelectSparseFullOverlapJoinOp>(result_type().cell_meta().limit(), function(), single_dim);
    return InterpretedFunction::Instruction(op, wrap_param<JoinParam>(param));
}

bool
SparseFullOverlapJoinFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    if ((lhs.cell_type() == rhs.cell_type()) &&
        (res.cell_type() == lhs.cell_type()) &&
        is_sparse_like(lhs) && is_sparse_like(rhs) &&
        (res.count_mapped_dimensions() == lhs.count_mapped_dimensions()) &&
        (res.count_mapped_dimensions() == rhs.count_mapped_dimensions()))
    {
        assert(is_sparse_like(res));
        return true;
    }
    return false;
}

const TensorFunction &
SparseFullOverlapJoinFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (compatible_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
            return stash.create<SparseFullOverlapJoinFunction>(*join);
        }
    }
    return expr;
}

} // namespace
