// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_cell_range_function.h"
#include "dense_tensor_view.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::tensor {

using eval::Value;
using eval::ValueType;
using eval::TensorFunction;
using eval::TensorEngine;
using eval::as;
using namespace eval::tensor_function;

namespace {

template <typename CT>
void my_cell_range_op(eval::InterpretedFunction::State &state, uint64_t param) {
    const auto &self = unwrap_param<DenseCellRangeFunction>(param);
    auto old_cells = state.peek(0).cells().typify<CT>();
    ConstArrayRef<CT> new_cells(&old_cells[self.offset()], self.length());
    state.pop_push(state.stash.create<DenseTensorView>(self.result_type(), TypedCells(new_cells)));
}

struct MyCellRangeOp {
    template <typename CT>
    static auto invoke() { return my_cell_range_op<CT>; }
};

} // namespace vespalib::tensor::<unnamed>

DenseCellRangeFunction::DenseCellRangeFunction(const eval::ValueType &result_type,
                                               const eval::TensorFunction &child,
                                               size_t offset, size_t length)
    : eval::tensor_function::Op1(result_type, child),
      _offset(offset),
      _length(length)
{
}

DenseCellRangeFunction::~DenseCellRangeFunction() = default;

eval::InterpretedFunction::Instruction
DenseCellRangeFunction::compile_self(const TensorEngine &, Stash &) const
{
    assert(result_type().cell_type() == child().result_type().cell_type());

    using MyTypify = eval::TypifyCellType;
    auto op = typify_invoke<1,MyTypify,MyCellRangeOp>(result_type().cell_type());
    return eval::InterpretedFunction::Instruction(op, wrap_param<DenseCellRangeFunction>(*this));
}

} // namespace vespalib::tensor
