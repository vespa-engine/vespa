// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_merge.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/value_builder_factory.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

MergeParam::~MergeParam() = default;

//-----------------------------------------------------------------------------

template <typename LCT, typename RCT, typename OCT, typename Fun>
std::unique_ptr<Value>
generic_mixed_merge(const Value &a, const Value &b,
                    const MergeParam &params)
{
    Fun fun(params.function);
    auto lhs_cells = a.cells().typify<LCT>();
    auto rhs_cells = b.cells().typify<RCT>();
    const size_t num_mapped = params.num_mapped_dimensions;
    const size_t subspace_size = params.dense_subspace_size;
    size_t guess_subspaces = std::max(a.index().size(), b.index().size());
    auto builder = params.factory.create_transient_value_builder<OCT>(params.res_type, num_mapped, subspace_size, guess_subspaces);
    SmallVector<string_id> address(num_mapped);
    SmallVector<const string_id *> addr_cref;
    SmallVector<string_id *> addr_ref;
    for (auto & ref : address) {
        addr_cref.push_back(&ref);
        addr_ref.push_back(&ref);
    }
    size_t lhs_subspace;
    size_t rhs_subspace;
    auto inner = b.index().create_view(params.all_view_dims);
    auto outer = a.index().create_view({});
    outer->lookup({});
    while (outer->next_result(addr_ref, lhs_subspace)) {
        OCT *dst = builder->add_subspace(address).begin();
        inner->lookup(addr_cref);
        if (inner->next_result({}, rhs_subspace)) {
            const LCT *lhs_src = &lhs_cells[lhs_subspace * subspace_size];
            const RCT *rhs_src = &rhs_cells[rhs_subspace * subspace_size];
            for (size_t i = 0; i < subspace_size; ++i) {
                *dst++ = fun(*lhs_src++, *rhs_src++);
            }
        } else {
            const LCT *src = &lhs_cells[lhs_subspace * subspace_size];
            for (size_t i = 0; i < subspace_size; ++i) {
                *dst++ = *src++;
            }
        }
    }
    inner = a.index().create_view(params.all_view_dims);
    outer = b.index().create_view({});
    outer->lookup({});
    while (outer->next_result(addr_ref, rhs_subspace)) {
        inner->lookup(addr_cref);
        if (! inner->next_result({}, lhs_subspace)) {
            OCT *dst = builder->add_subspace(address).begin();
            const RCT *src = &rhs_cells[rhs_subspace * subspace_size];
            for (size_t i = 0; i < subspace_size; ++i) {
                *dst++ = *src++;
            }
        }
    }
    return builder->build(std::move(builder));
}


namespace {

template <typename LCT, typename RCT, typename OCT, typename Fun>
void my_mixed_merge_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<MergeParam>(param_in);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    auto up = generic_mixed_merge<LCT, RCT, OCT, Fun>(lhs, rhs, param);
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    state.pop_pop_push(result_ref);
};

struct SelectGenericMergeOp {
    template <typename LCM, typename RCM, typename Fun> static auto invoke() {
        using LCT = CellValueType<LCM::value.cell_type>;
        using RCT = CellValueType<RCM::value.cell_type>;
        constexpr CellMeta ocm = CellMeta::merge(LCM::value, RCM::value);
        using OCT = CellValueType<ocm.cell_type>;
        return my_mixed_merge_op<LCT,RCT,OCT,Fun>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

using MergeTypify = TypifyValue<TypifyCellMeta,operation::TypifyOp2>;

Instruction
GenericMerge::make_instruction(const ValueType &result_type,
                               const ValueType &lhs_type, const ValueType &rhs_type, join_fun_t function,
                               const ValueBuilderFactory &factory, Stash &stash)
{
    const auto &param = stash.create<MergeParam>(result_type, lhs_type, rhs_type, function, factory);
    assert(result_type == ValueType::merge(lhs_type, rhs_type));
    auto fun = typify_invoke<3,MergeTypify,SelectGenericMergeOp>(lhs_type.cell_meta(), rhs_type.cell_meta(), function);
    return Instruction(fun, wrap_param<MergeParam>(param));
}

} // namespace
