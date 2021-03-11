// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_map.h"
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <cassert>

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;
using vespalib::eval::tensor_function::unwrap_param;
using vespalib::eval::tensor_function::wrap_param;

struct MapParam {
    const ValueType res_type;
    const map_fun_t function;
    MapParam(const ValueType &r, map_fun_t f) : res_type(r), function(f) {}
};

namespace {

template <typename ICT, typename OCT, typename Func>
void my_generic_map_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<MapParam>(param_in);
    Func function(param.function);
    const Value &a = state.peek(0);
    auto input_cells = a.cells().typify<ICT>();
    auto output_cells = state.stash.create_uninitialized_array<OCT>(input_cells.size());
    auto pos = output_cells.begin();
    if constexpr (std::is_same<ICT,OCT>::value) {
        apply_op1_vec(pos, input_cells.begin(), output_cells.size(), function);
    } else {
        for (ICT value : input_cells) {
            *pos++ = (OCT) function(value);
        }
        assert(pos == output_cells.end());
    }
    Value &result_ref = state.stash.create<ValueView>(param.res_type, a.index(), TypedCells(output_cells));
    state.pop_push(result_ref);
}

template <typename Func>
void my_double_map_op(State &state, uint64_t param_in) {
    const auto &param = unwrap_param<MapParam>(param_in);
    Func fun(param.function);
    state.pop_push(state.stash.create<DoubleValue>(fun(state.peek(0).as_double())));
}

struct SelectGenericMapOp {
    template <typename ICM, typename Func> static auto invoke() {
        if constexpr (ICM::value.is_scalar) {
            return my_double_map_op<Func>;
        } else {
            using ICT = CellValueType<ICM::value.cell_type>;
            using OCT = CellValueType<ICM::value.map().cell_type>;
            return my_generic_map_op<ICT, OCT, Func>;
        }
    }
};

} // namespace <unnamed>

using MapTypify = TypifyValue<TypifyCellMeta,operation::TypifyOp1>;
 
InterpretedFunction::Instruction
GenericMap::make_instruction(const ValueType &result_type,
                             const ValueType &input_type,
                             map_fun_t function,
                             Stash &stash)
{
    const auto &param = stash.create<MapParam>(result_type, function);
    assert(result_type == input_type.map());
    auto op = typify_invoke<2,MapTypify,SelectGenericMapOp>(input_type.cell_meta(), function);
    return Instruction(op, wrap_param<MapParam>(param));
}

} // namespace
