// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_create.h"
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/eval/eval/array_array_map.h>
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
    const ValueType res_type;
    size_t num_mapped_dims;
    size_t dense_subspace_size;
    size_t num_children;
    ArrayArrayMap<Handle,size_t> my_spec;
    const ValueBuilderFactory &factory;

    static constexpr size_t npos = -1;

    ArrayRef<size_t> indexes(ConstArrayRef<Handle> key) {
        auto [tag, first_time] = my_spec.lookup_or_add_entry(key);
        auto rv = my_spec.get_values(tag);
        if (first_time) {
            for (auto & v : rv) {
                v = npos;
            }
        }
        return rv;
    }

    CreateParam(const ValueType &res_type_in,
                const GenericCreate::SpecMap &spec_in,
                const ValueBuilderFactory &factory_in)
        : res_type(res_type_in),
          num_mapped_dims(res_type.count_mapped_dimensions()),
          dense_subspace_size(res_type.dense_subspace_size()),
          num_children(spec_in.size()),
          my_spec(num_mapped_dims, dense_subspace_size, spec_in.size() / dense_subspace_size),
          factory(factory_in)
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
            assert(dense_key < dense_subspace_size);
            // note: reverse order of children on stack
            size_t stack_idx = last_child - entry.second;
            indexes(sparse_key)[dense_key] = stack_idx;
        }
    }
};

template <typename T>
void my_generic_create_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<CreateParam>(param_in);
    auto builder = param.factory.create_transient_value_builder<T>(param.res_type,
                                                                   param.num_mapped_dims,
                                                                   param.dense_subspace_size,
                                                                   param.my_spec.size());
    SmallVector<string_id> sparse_addr;
    param.my_spec.each_entry([&](const auto &key, const auto &values)
        {
            sparse_addr.clear();
            for (const auto & label : key) {
                sparse_addr.push_back(label.id());
            }
            T *dst = builder->add_subspace(sparse_addr).begin();
            for (size_t stack_idx : values) {
                if (stack_idx == CreateParam::npos) {
                    *dst++ = T{};
                } else {
                    const Value &child = state.peek(stack_idx);
                    *dst++ = child.as_double();
                }
            }
        });
    const Value &result = *state.stash.create<Value::UP>(builder->build(std::move(builder)));
    state.pop_n_push(param.num_children, result);
};

struct SelectGenericCreateOp {
    template <typename T> static auto invoke() {
        return my_generic_create_op<T>;
    }
};

//-----------------------------------------------------------------------------

} // namespace <unnamed>

Instruction
GenericCreate::make_instruction(const ValueType &result_type,
                                const SpecMap &spec,
                                const ValueBuilderFactory &factory,
                                Stash &stash)
{
    const auto &param = stash.create<CreateParam>(result_type, spec, factory);
    auto fun = typify_invoke<1,TypifyCellType,SelectGenericCreateOp>(result_type.cell_type());
    return Instruction(fun, wrap_param<CreateParam>(param));
}

} // namespace
