// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_cell_order.h"
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/wrap_param.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/hwaccelerated/iaccelerated.h>
#include <cassert>
#include <numeric>

using namespace vespalib::eval::tensor_function;

namespace vespalib::eval::instruction {

using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

namespace {

template <typename ICT, typename OCT, typename CMP>
void my_generic_cell_order_op(State &state, uint64_t param_in) {
    const auto &res_type = unwrap_param<ValueType>(param_in);
    const Value &input = state.peek(0);
    auto input_cells = input.cells().typify<ICT>();
    auto output_cells = state.stash.create_uninitialized_array<OCT>(input_cells.size());
    {
        auto before_indexes = state.stash.mark();
        auto indexes = state.stash.create_uninitialized_array<OCT>(input_cells.size());
        std::iota(indexes.begin(), indexes.end(), 0);
        std::sort(indexes.begin(), indexes.end(), [&](auto a, auto b) {
            return CMP::cmp(input_cells[a], input_cells[b]);
        });
        for (size_t rank = 0; rank < output_cells.size(); ++rank) {
            output_cells[indexes[rank]] = rank;
        }
        state.stash.revert(before_indexes);
    }
    Value &result_ref = state.stash.create<ValueView>(res_type, input.index(), TypedCells(output_cells));
    state.pop_push(result_ref);
}

struct SelectGenericCellOrderOp {
    template <typename CM>
    static InterpretedFunction::op_function invoke(CellOrder cell_order) {
        using ICT = CellValueType<CM::value.cell_type>;
        using OCT = CellValueType<CM::value.decay().cell_type>;
        switch (cell_order) {
        case CellOrder::MAX: return my_generic_cell_order_op<ICT,OCT,CellOrderMAX>;
        case CellOrder::MIN: return my_generic_cell_order_op<ICT,OCT,CellOrderMIN>;
        }
        abort();
    }
};

} // namespace <unnamed>

InterpretedFunction::Instruction
GenericCellOrder::make_instruction(const ValueType &result_type,
                                  const ValueType &input_type,
                                  CellOrder cell_order,
                                  Stash &stash)
{
    assert(input_type.count_mapped_dimensions() > 0);
    assert(result_type == input_type.map());
    auto &param = stash.create<ValueType>(result_type);
    auto op = typify_invoke<1,TypifyCellMeta,SelectGenericCellOrderOp>(input_type.cell_meta().not_scalar(),
                                                                       cell_order);
    return {op, wrap_param<ValueType>(param)};
}

} // namespace
