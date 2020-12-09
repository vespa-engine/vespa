// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dense_replace_type_function.h"
#include <vespa/eval/eval/value.h>

namespace vespalib::eval {

using namespace tensor_function;

namespace {

void my_replace_type_op(InterpretedFunction::State &state, uint64_t param) {
    const ValueType &type = unwrap_param<ValueType>(param);
    TypedCells cells = state.peek(0).cells();
    state.pop_push(state.stash.create<DenseValueView>(type, cells));
}

} // namespace vespalib::eval::<unnamed>

DenseReplaceTypeFunction::DenseReplaceTypeFunction(const ValueType &result_type,
                                                   const TensorFunction &child)
    : tensor_function::Op1(result_type, child)
{
}

DenseReplaceTypeFunction::~DenseReplaceTypeFunction()
{
}

InterpretedFunction::Instruction
DenseReplaceTypeFunction::compile_self(const ValueBuilderFactory &, Stash &) const
{
    return InterpretedFunction::Instruction(my_replace_type_op, wrap_param<ValueType>(result_type()));
}

const DenseReplaceTypeFunction &
DenseReplaceTypeFunction::create_compact(const ValueType &result_type,
                                         const TensorFunction &child,
                                         Stash &stash)
{
    if (auto replace = as<DenseReplaceTypeFunction>(child)) {
        return stash.create<DenseReplaceTypeFunction>(result_type, replace->child());
    } else {
        return stash.create<DenseReplaceTypeFunction>(result_type, child);
    }
}

} // namespace vespalib::eval
