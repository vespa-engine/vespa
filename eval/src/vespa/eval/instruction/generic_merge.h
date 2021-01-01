// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "generic_join.h"

namespace vespalib::eval::instruction {

struct GenericMerge {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &lhs_type, const ValueType &rhs_type,
                     join_fun_t function,
                     const ValueBuilderFactory &factory, Stash &stash);
};

} // namespace
