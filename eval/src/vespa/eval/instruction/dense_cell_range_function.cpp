// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_cell_range_function.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using namespace tensor_function;

namespace {

template <typename CT>
void my_cell_range_op(InterpretedFunction::State &state, uint64_t param) {
    const auto &self = unwrap_param<DenseCellRangeFunction>(param);
    auto old_cells = state.peek(0).cells().typify<CT>();
    ConstArrayRef<CT> new_cells(&old_cells[self.offset()], self.length());
    state.pop_push(state.stash.create<DenseValueView>(self.result_type(), TypedCells(new_cells)));
}

struct MyCellRangeOp {
    template <typename CT>
    static auto invoke() { return my_cell_range_op<CT>; }
};

} // namespace <unnamed>

DenseCellRangeFunction::DenseCellRangeFunction(const ValueType &result_type,
                                               const TensorFunction &child,
                                               size_t offset, size_t length)
    : tensor_function::Op1(result_type, child),
      _offset(offset),
      _length(length)
{
    assert(result_type.cell_type() == child.result_type().cell_type());
}

DenseCellRangeFunction::~DenseCellRangeFunction() = default;

InterpretedFunction::Instruction
DenseCellRangeFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    assert(result_type().cell_type() == child().result_type().cell_type());

    using MyTypify = TypifyCellType;
    auto op = typify_invoke<1,MyTypify,MyCellRangeOp>(result_type().cell_type());
    return InterpretedFunction::Instruction(op, wrap_param<DenseCellRangeFunction>(*this));
}

} // namespace
