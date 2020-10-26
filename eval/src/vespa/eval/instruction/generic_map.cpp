// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_map.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

uint64_t to_param(map_fun_t value) { return (uint64_t)value; }
map_fun_t to_map_fun(uint64_t param) { return (map_fun_t)param; }

template <typename CT, typename Func>
void my_generic_map_op(State &state, uint64_t param_in) {
    Func function(to_map_fun(param_in));
    const Value &a = state.peek(0);
    auto input_cells = a.cells().typify<CT>();
    auto output_cells = state.stash.create_uninitialized_array<CT>(input_cells.size());
    auto pos = output_cells.begin();
    for (CT value : input_cells) {
        *pos++ = (CT) function(value);
    }
    assert(pos == output_cells.end());
    Value &result_ref = state.stash.create<ValueView>(a.type(), a.index(), TypedCells(output_cells));
    state.pop_push(result_ref);
}

template <typename Func>
void my_double_map_op(State &state, uint64_t param_in) {
    Func function(to_map_fun(param_in));
    const Value &a = state.peek(0);
    state.pop_push(state.stash.create<DoubleValue>(function(a.as_double())));
}

struct SelectGenericMapOp {
    template <typename CT, typename Func> static auto invoke(const ValueType &type) {
        if (type.is_double()) {
            assert((std::is_same<CT,double>::value));
            return my_double_map_op<Func>;
        }
        return my_generic_map_op<CT, Func>;
    }
};

struct PerformGenericMap {
    template <typename CT, typename Func>
    static auto invoke(const Value &input, map_fun_t function_in,
                       const ValueBuilderFactory &factory)
    {
        Func fun(function_in);
        const auto &type = input.type();
        size_t subspace_size = type.dense_subspace_size();
        size_t num_mapped = type.count_mapped_dimensions();
        auto builder = factory.create_value_builder<CT>(type, num_mapped, subspace_size, input.index().size());
        auto input_cells = input.cells().typify<CT>();
        auto view = input.index().create_view({});
        std::vector<vespalib::stringref> output_address(num_mapped);
        std::vector<vespalib::stringref *> input_address;
        for (auto & label : output_address) {
            input_address.push_back(&label);
        }
        view->lookup({});
        size_t subspace;
        while (view->next_result(input_address, subspace)) {
            auto dst = builder->add_subspace(output_address);
            size_t input_offset = subspace_size * subspace;
            for (size_t i = 0; i < subspace_size; ++i) {
                dst[i] = fun(input_cells[input_offset + i]);
            }
        }
        return builder->build(std::move(builder));
    }
};

} // namespace <unnamed>

using MapTypify = TypifyValue<TypifyCellType,operation::TypifyOp1>;
 
InterpretedFunction::Instruction
GenericMap::make_instruction(const ValueType &lhs_type, map_fun_t function)
{
    auto op = typify_invoke<2,MapTypify,SelectGenericMapOp>(lhs_type.cell_type(), function, lhs_type);
    return Instruction(op, to_param(function));
}

Value::UP
GenericMap::perform_map(const Value &a, map_fun_t function,
                        const ValueBuilderFactory &factory)
{
    return typify_invoke<2,MapTypify,PerformGenericMap>(a.type().cell_type(), function,
                                                        a, function, factory);
}

} // namespace
