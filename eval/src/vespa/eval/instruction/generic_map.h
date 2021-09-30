// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/interpreted_function.h>

namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

using map_fun_t = operation::op1_t;

struct GenericMap {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &result_type,
                     const ValueType &input_type,
                     map_fun_t function,
                     Stash &stash);
};

} // namespace
