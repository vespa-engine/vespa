// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_cell_cast.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

template <typename ICT, typename OCT>
void my_generic_cell_cast_op(State &state, uint64_t param_in) {
    const auto &res_type = unwrap_param<ValueType>(param_in);
    const Value &a = state.peek(0);
    auto input_cells = a.cells().typify<ICT>();
    auto output_cells = state.stash.create_uninitialized_array<OCT>(input_cells.size());
    auto pos = output_cells.begin();
    for (ICT value : input_cells) {
        *pos++ = (OCT) value;
    }
    assert(pos == output_cells.end());
    Value &result_ref = state.stash.create<ValueView>(res_type, a.index(), TypedCells(output_cells));
    state.pop_push(result_ref);
}

struct SelectGenericCellCastOp {
    template <typename ICT, typename OCT>
    static auto invoke() {
        return my_generic_cell_cast_op<ICT, OCT>;
    }
};

} // namespace <unnamed>

InterpretedFunction::Instruction
GenericCellCast::make_instruction(const ValueType &input_type,
                                  CellType to_cell_type,
                                  Stash &stash)
{
    CellType from = input_type.cell_type();
    auto result_type = ValueType::cell_cast(input_type, to_cell_type);
    auto &param = stash.create<ValueType>(result_type);
    CellType to = result_type.cell_type();
    auto op = typify_invoke<2,TypifyCellType,SelectGenericCellCastOp>(from, to);
    return Instruction(op, wrap_param<ValueType>(param));
}

} // namespace
