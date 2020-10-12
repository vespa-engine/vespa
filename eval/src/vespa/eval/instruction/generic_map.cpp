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
    auto output_cells = state.stash.create_array<CT>(input_cells.size());
    auto pos = output_cells.begin();
    for (CT value : input_cells) {
        *pos++ = (CT) function(value);
    }
    assert(pos == output_cells.end());
    Value &result_ref = state.stash.create<MixedValueView>(a.type(), a.index(), TypedCells(output_cells));
    state.pop_push(result_ref);
}

struct SelectGenericMapOp {
    template <typename CT, typename Func> static auto invoke() {
        return my_generic_map_op<CT, Func>;
    }
};

} // namespace <unnamed>

using MapTypify = TypifyValue<TypifyCellType,operation::TypifyOp1>;
 
InterpretedFunction::Instruction
GenericMap::make_instruction(const ValueType &lhs_type, map_fun_t function, Stash &)
{
    auto op = typify_invoke<2,MapTypify,SelectGenericMapOp>(lhs_type.cell_type(), function);
    return Instruction(op, to_param(function));
}

} // namespace
