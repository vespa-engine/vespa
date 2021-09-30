// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "replace_type_function.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using namespace tensor_function;

namespace {

void my_replace_type_op(InterpretedFunction::State &state, uint64_t param) {
    const ValueType &type = unwrap_param<ValueType>(param);
    TypedCells cells = state.peek(0).cells();
    const auto & idx = state.peek(0).index();
    state.pop_push(state.stash.create<ValueView>(type, idx, cells));
}

} // namespace vespalib::eval::<unnamed>

ReplaceTypeFunction::ReplaceTypeFunction(const ValueType &result_type,
                                         const TensorFunction &child)
    : tensor_function::Op1(result_type, child)
{
}

ReplaceTypeFunction::~ReplaceTypeFunction()
{
}

InterpretedFunction::Instruction
ReplaceTypeFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return InterpretedFunction::Instruction(my_replace_type_op, wrap_param<ValueType>(result_type()));
}

const ReplaceTypeFunction &
ReplaceTypeFunction::create_compact(const ValueType &result_type,
                                    const TensorFunction &child,
                                    Stash &stash)
{
    if (auto replace = as<ReplaceTypeFunction>(child)) {
        return stash.create<ReplaceTypeFunction>(result_type, replace->child());
    } else {
        return stash.create<ReplaceTypeFunction>(result_type, child);
    }
}

} // namespace vespalib::eval
