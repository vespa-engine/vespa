// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::eval::instruction {

struct GenericLambda {
    static InterpretedFunction::Instruction
    make_instruction(const tensor_function::Lambda &lambda_in,
                     const ValueBuilderFactory &factory, Stash &stash);
};

} // namespace
