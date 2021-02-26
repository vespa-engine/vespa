// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/interpreted_function.h>

namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

struct GenericCellCast {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &result_type,
                     const ValueType &input_type, CellType to_cell_type,
                     Stash &stash);
};

} // namespace
