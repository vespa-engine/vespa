// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_cell_cast.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <cassert>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

using hwaccelerated::IAccelerated;

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

template <>
void my_generic_cell_cast_op<BFloat16, float>(State &state, uint64_t param_in) {
    const auto &res_type = unwrap_param<ValueType>(param_in);
    const Value &a = state.peek(0);
    auto input_cells = a.cells().typify<BFloat16>();
    auto output_cells = state.stash.create_uninitialized_array<float>(input_cells.size());
    static const IAccelerated & accelrator = IAccelerated::getAccelerator();
    accelrator.convert_bfloat16_to_float(reinterpret_cast<const uint16_t *>(input_cells.data()),
                                         output_cells.data(), output_cells.size());
    Value &result_ref = state.stash.create<ValueView>(res_type, a.index(), TypedCells(output_cells));
    state.pop_push(result_ref);
}

struct SelectGenericCellCastOp {
    template <typename ICT, typename OCT>
    static InterpretedFunction::op_function invoke() {
        if constexpr (std::is_same_v<ICT,OCT>) {
            // handeled by nop case below
            abort();
        } else {
            return my_generic_cell_cast_op<ICT, OCT>;
        }
    }
};

} // namespace <unnamed>

InterpretedFunction::Instruction
GenericCellCast::make_instruction(const ValueType &result_type,
                                  const ValueType &input_type,
                                  CellType to_cell_type,
                                  Stash &stash)
{
    assert(result_type == input_type.cell_cast(to_cell_type));
    auto from = input_type.cell_type();
    auto to = result_type.cell_type();
    if (to == from) {
        return Instruction::nop();
    } else {
        assert(!input_type.is_double());
        auto &param = stash.create<ValueType>(result_type);
        auto op = typify_invoke<2,TypifyCellType,SelectGenericCellCastOp>(from, to);
        return {op, wrap_param<ValueType>(param)};
    }
}

} // namespace
