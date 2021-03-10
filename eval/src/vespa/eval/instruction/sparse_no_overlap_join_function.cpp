// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sparse_no_overlap_join_function.h"
#include "generic_join.h"
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/typify.h>

namespace vespalib::eval {

using namespace tensor_function;
using namespace operation;
using namespace instruction;

namespace {

template <typename CT, typename Fun>
const Value &my_fast_no_overlap_sparse_join(const FastAddrMap &lhs_map, const FastAddrMap &rhs_map,
                                            const CT *lhs_cells, const CT *rhs_cells,
                                            const JoinParam &param, Stash &stash)
{
    Fun fun(param.function);
    const auto &addr_sources = param.sparse_plan.sources;
    size_t num_mapped_dims = addr_sources.size();
    auto &result = stash.create<FastValue<CT,true>>(param.res_type, num_mapped_dims, 1, lhs_map.size() * rhs_map.size());
    SmallVector<string_id> output_addr(num_mapped_dims);
    SmallVector<size_t> store_lhs_idx;
    SmallVector<size_t> store_rhs_idx;
    size_t out_idx = 0;
    for (auto source: addr_sources) {
        switch (source) {
        case SparseJoinPlan::Source::LHS:
            store_lhs_idx.push_back(out_idx++);
            break;
        case SparseJoinPlan::Source::RHS:
            store_rhs_idx.push_back(out_idx++);
            break;
        default: abort();
        }
    }
    assert(out_idx == output_addr.size());
    for (size_t lhs_subspace = 0; lhs_subspace < lhs_map.size(); ++lhs_subspace) {
        auto l_addr = lhs_map.get_addr(lhs_subspace);
        assert(l_addr.size() == store_lhs_idx.size());
        for (size_t i = 0; i < store_lhs_idx.size(); ++i) {
            size_t addr_idx = store_lhs_idx[i];
            output_addr[addr_idx] = l_addr[i];
        }
        for (size_t rhs_subspace = 0; rhs_subspace < rhs_map.size(); ++rhs_subspace) {
            auto r_addr = rhs_map.get_addr(rhs_subspace);
            assert(r_addr.size() == store_rhs_idx.size());
            for (size_t i = 0; i < store_rhs_idx.size(); ++i) {
                size_t addr_idx = store_rhs_idx[i];
                output_addr[addr_idx] = r_addr[i];
            }
            result.add_mapping(ConstArrayRef(output_addr));
            CT cell_value = fun(lhs_cells[lhs_subspace], rhs_cells[rhs_subspace]);
            result.my_cells.push_back_fast(cell_value);
        }
    }
    return result;
}

template <typename CT, typename Fun>
void my_sparse_no_overlap_join_op(InterpretedFunction::State &state, uint64_t param_in) {
    const auto &param = unwrap_param<JoinParam>(param_in);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    const auto &lhs_idx = lhs.index();
    const auto &rhs_idx = rhs.index();
    if (__builtin_expect(are_fast(lhs_idx, rhs_idx), true)) {
        const Value &res = my_fast_no_overlap_sparse_join<CT,Fun>(as_fast(lhs_idx).map, as_fast(rhs_idx).map,
                lhs.cells().typify<CT>().cbegin(), rhs.cells().typify<CT>().cbegin(), param, state.stash);
        state.pop_pop_push(res);
    } else {
        auto res = generic_mixed_join<CT,CT,CT,Fun>(lhs, rhs, param);
        state.pop_pop_push(*state.stash.create<std::unique_ptr<Value>>(std::move(res)));
    }
}

struct SelectSparseNoOverlapJoinOp {
    template <typename R1, typename Fun>
    static auto invoke() {
        using CT = CellValueType<R1::value.cell_type>;
        return my_sparse_no_overlap_join_op<CT,Fun>;
    }
};

using MyTypify = TypifyValue<TypifyCellMeta,operation::TypifyOp2>;

bool is_sparse_like(const ValueType &type) {
    return ((type.count_mapped_dimensions() > 0) && (type.dense_subspace_size() == 1));
}

} // namespace <unnamed>

SparseNoOverlapJoinFunction::SparseNoOverlapJoinFunction(const tensor_function::Join &original)
    : tensor_function::Join(original.result_type(),
                            original.lhs(),
                            original.rhs(),
                            original.function())
{
    assert(compatible_types(result_type(), lhs().result_type(), rhs().result_type()));
}

InterpretedFunction::Instruction
SparseNoOverlapJoinFunction::compile_self(const ValueBuilderFactory &factory, Stash &stash) const
{
    const auto &param = stash.create<JoinParam>(result_type(),
                                                lhs().result_type(), rhs().result_type(),
                                                function(), factory);
    auto op = typify_invoke<2,MyTypify,SelectSparseNoOverlapJoinOp>(result_type().cell_meta().limit(), function());
    return InterpretedFunction::Instruction(op, wrap_param<JoinParam>(param));
}

bool
SparseNoOverlapJoinFunction::compatible_types(const ValueType &res, const ValueType &lhs, const ValueType &rhs)
{
    if ((lhs.cell_type() == rhs.cell_type()) &&
        (res.cell_type() == lhs.cell_type()) &&
        is_sparse_like(lhs) && is_sparse_like(rhs) &&
        (res.count_mapped_dimensions() == (lhs.count_mapped_dimensions() + rhs.count_mapped_dimensions())))
    {
        assert(is_sparse_like(res));
        return true;
    }
    return false;
}

const TensorFunction &
SparseNoOverlapJoinFunction::optimize(const TensorFunction &expr, Stash &stash)
{
    if (auto join = as<Join>(expr)) {
        const TensorFunction &lhs = join->lhs();
        const TensorFunction &rhs = join->rhs();
        if (compatible_types(expr.result_type(), lhs.result_type(), rhs.result_type())) {
            return stash.create<SparseNoOverlapJoinFunction>(*join);
        }
    }
    return expr;
}

} // namespace
