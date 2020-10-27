// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_create.h"
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

struct CreateParam {
    using Key = std::vector<vespalib::string>;
    using Indexes = std::vector<size_t>;

    const ValueType res_type;
    size_t num_mapped_dims;
    size_t dense_subspace_size;
    size_t num_children;
    std::map<Key,Indexes> my_spec;
    const ValueBuilderFactory &factory;

    static constexpr size_t npos = -1;

    Indexes &indexes(Key key) {
        auto iter = my_spec.find(key);
        if (iter == my_spec.end()) {
            Indexes empty(dense_subspace_size, npos);
            iter = my_spec.emplace(key, empty).first;
        }
        return iter->second;
    }

    CreateParam(const std::vector<TensorSpec::Address> &addresses,
                const ValueType &res_type_in,
                const ValueBuilderFactory &factory_in)
        : res_type(res_type_in),
          num_mapped_dims(res_type.count_mapped_dimensions()),
          dense_subspace_size(res_type.dense_subspace_size()),
          num_children(addresses.size()),
          my_spec(),
          factory(factory_in)
    {
        size_t child_idx = 0;
        for (const auto & addr : addresses) {
            Key sparse_addr;
            size_t dense_idx = 0;
            for (const auto &dim : res_type.dimensions()) {
                auto iter = addr.find(dim.name);
                if (dim.is_mapped()) {
                    sparse_addr.push_back(iter->second.name);
                } else {
                    assert(dim.is_indexed());
                    dense_idx *= dim.size;
                    dense_idx += iter->second.index;
                }
            }
            indexes(sparse_addr)[dense_idx] = child_idx++;
        }
    }
};

template <typename T>
void my_generic_create_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<CreateParam>(param_in);
    auto builder = param.factory.create_value_builder<T>(param.res_type,
                                                           param.num_mapped_dims,
                                                           param.dense_subspace_size,
                                                           param.my_spec.size());
    std::vector<vespalib::stringref> sparse_addr;
    for (const auto & kv : param.my_spec) {
        sparse_addr.clear();
        for (const auto & label : kv.first) {
            sparse_addr.emplace_back(label);
        }
        T *dst = builder->add_subspace(sparse_addr).begin();
        for (size_t child_idx : kv.second) {
            if (child_idx == CreateParam::npos) {
                *dst++ = T{};
            } else {
                *dst++ = state.peek(child_idx).as_double();
            }
        }
    }        
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
GenericCreate::make_instruction(const std::vector<TensorSpec::Address> &addresses,
                                const ValueType &res_type,
                                const ValueBuilderFactory &factory,
                                Stash &stash)
{
    const auto &param = stash.create<CreateParam>(addresses, res_type, factory);
    auto fun = typify_invoke<1,TypifyCellType,SelectGenericCreateOp>(res_type.cell_type());
    return Instruction(fun, wrap_param<CreateParam>(param));
}

} // namespace
