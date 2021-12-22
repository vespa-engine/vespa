// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_create.h"
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/shared_string_repo.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;
using Handle = SharedStringRepo::Handle;

namespace {

struct CreateParam {
    static constexpr uint32_t npos = -1;
    FastValue<uint32_t, false> my_spec;
    size_t num_children;

    ArrayRef<uint32_t> indexes(ConstArrayRef<Handle> key) {
        SmallVector<string_id> my_key;
        for (const auto &label: key) {
            my_key.push_back(label.id());
        }
        auto old_subspace = my_spec.my_index.map.lookup(ConstArrayRef<string_id>(my_key));
        if (old_subspace != FastAddrMap::npos()) {
            return my_spec.get_subspace(old_subspace);
        }
        auto new_subspace = my_spec.add_subspace(my_key);
        for (auto &stack_idx: new_subspace) {
            stack_idx = npos;
        }
        return new_subspace;
    }

    CreateParam(const ValueType &res_type,
                const GenericCreate::SpecMap &spec_in)
      : my_spec(res_type,
                res_type.count_mapped_dimensions(),
                res_type.dense_subspace_size(),
                spec_in.size() / res_type.dense_subspace_size()),
        num_children(spec_in.size())
    {
        size_t last_child = num_children - 1;
        for (const auto & entry : spec_in) {
            SmallVector<Handle> sparse_key;
            size_t dense_key = 0;
            auto dim = res_type.dimensions().begin();
            auto binding = entry.first.begin();
            for (; dim != res_type.dimensions().end(); ++dim, ++binding) {
                assert(binding != entry.first.end());
                assert(dim->name == binding->first);
                assert(dim->is_mapped() == binding->second.is_mapped());
                if (dim->is_mapped()) {
                    sparse_key.push_back(Handle(binding->second.name));
                } else {
                    assert(binding->second.index < dim->size);
                    dense_key = (dense_key * dim->size) + binding->second.index;
                }
            }
            assert(binding == entry.first.end());
            // note: reverse order of children on stack
            size_t stack_idx = last_child - entry.second;
            indexes(sparse_key)[dense_key] = stack_idx;
        }
    }
};

template <typename CT>
void my_generic_create_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<CreateParam>(param_in);
    auto spec = param.my_spec.get_raw_cells();
    auto cells = state.stash.create_uninitialized_array<CT>(spec.size());
    CT *dst = cells.begin();
    for (uint32_t stack_idx: spec) {
        *dst++ = ((stack_idx != CreateParam::npos)
                  ? (CT) state.peek(stack_idx).as_double()
                  : CT{});
    }
    const Value &result = state.stash.create<ValueView>(param.my_spec.type(), param.my_spec.my_index, TypedCells(cells));
    state.pop_n_push(param.num_children, result);
};

struct SelectGenericCreateOp {
    template <typename CT> static auto invoke() {
        return my_generic_create_op<CT>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

Instruction
GenericCreate::make_instruction(const ValueType &result_type,
                                const SpecMap &spec,
                                const ValueBuilderFactory &,
                                Stash &stash)
{
    const auto &param = stash.create<CreateParam>(result_type, spec);
    auto fun = typify_invoke<1,TypifyCellType,SelectGenericCreateOp>(result_type.cell_type());
    return Instruction(fun, wrap_param<CreateParam>(param));
}

} // namespace
