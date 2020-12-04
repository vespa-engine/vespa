// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "detect_type.h"
#include "generic_merge.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>
#include <typeindex>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

//-----------------------------------------------------------------------------

struct MergeParam {
    const ValueType res_type;
    const join_fun_t function;
    const size_t num_mapped_dimensions;
    const size_t dense_subspace_size;
    std::vector<size_t> all_view_dims;
    const ValueBuilderFactory &factory;
    MergeParam(const ValueType &lhs_type, const ValueType &rhs_type,
               join_fun_t function_in, const ValueBuilderFactory &factory_in)
        : res_type(ValueType::join(lhs_type, rhs_type)),
          function(function_in),
          num_mapped_dimensions(lhs_type.count_mapped_dimensions()),
          dense_subspace_size(lhs_type.dense_subspace_size()),
          all_view_dims(num_mapped_dimensions),
          factory(factory_in)
    {
        assert(!res_type.is_error());
        assert(num_mapped_dimensions == rhs_type.count_mapped_dimensions());
        assert(num_mapped_dimensions == res_type.count_mapped_dimensions());
        assert(dense_subspace_size == rhs_type.dense_subspace_size());
        assert(dense_subspace_size == res_type.dense_subspace_size());
        for (size_t i = 0; i < num_mapped_dimensions; ++i) {
            all_view_dims[i] = i;
        }
    }
    ~MergeParam();
};
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
    auto builder = params.factory.create_value_builder<OCT>(params.res_type, num_mapped, subspace_size, guess_subspaces);
    std::vector<vespalib::stringref> address(num_mapped);
    std::vector<const vespalib::stringref *> addr_cref;
    std::vector<vespalib::stringref *> addr_ref;
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

template <typename LCT, typename RCT, typename OCT, typename Fun>
void my_sparse_merge_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<MergeParam>(param_in);
    const Value &lhs = state.peek(1);
    const Value &rhs = state.peek(0);
    if (auto indexes = detect_type<FastValueIndex>(lhs.index(), rhs.index())) {
        auto lhs_cells = lhs.cells().typify<LCT>();
        auto rhs_cells = rhs.cells().typify<RCT>();
        if (lhs_cells.size() < rhs_cells.size()) {
            return state.pop_pop_push(
                FastValueIndex::sparse_only_merge<RCT,LCT,OCT,Fun>(
                    param.res_type, Fun(param.function),
                    indexes.get<1>(), indexes.get<0>(),
                    rhs_cells, lhs_cells, state.stash));
        } else {
            return state.pop_pop_push(
                FastValueIndex::sparse_only_merge<LCT,RCT,OCT,Fun>(
                    param.res_type, Fun(param.function),
                    indexes.get<0>(), indexes.get<1>(),
                    lhs_cells, rhs_cells, state.stash));
        }
    }
    auto up = generic_mixed_merge<LCT, RCT, OCT, Fun>(lhs, rhs, param);
    auto &result = state.stash.create<std::unique_ptr<Value>>(std::move(up));
    const Value &result_ref = *(result.get());
    state.pop_pop_push(result_ref);
};

struct SelectGenericMergeOp {
    template <typename LCT, typename RCT, typename OCT, typename Fun> static auto invoke(const MergeParam &param) {
        if (param.dense_subspace_size == 1) {
            return my_sparse_merge_op<LCT,RCT,OCT,Fun>;
        }
        return my_mixed_merge_op<LCT,RCT,OCT,Fun>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

using MergeTypify = TypifyValue<TypifyCellType,operation::TypifyOp2>;

Instruction
GenericMerge::make_instruction(const ValueType &lhs_type, const ValueType &rhs_type, join_fun_t function,
                               const ValueBuilderFactory &factory, Stash &stash)
{
    const auto &param = stash.create<MergeParam>(lhs_type, rhs_type, function, factory);
    auto fun = typify_invoke<4,MergeTypify,SelectGenericMergeOp>(lhs_type.cell_type(), rhs_type.cell_type(), param.res_type.cell_type(), function, param);
    return Instruction(fun, wrap_param<MergeParam>(param));
}

} // namespace
