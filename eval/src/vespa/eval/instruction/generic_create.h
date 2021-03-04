// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/tensor_function.h>
#include <map>

namespace vespalib { class Stash; }
namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

//-----------------------------------------------------------------------------

struct GenericCreate {
    // mapping from cell address to index of child that computes the cell value
    using SpecMap = tensor_function::Create::Spec;

    static InterpretedFunction::Instruction
    make_instruction(const ValueType &result_type,
                     const SpecMap &spec,
                     const ValueBuilderFactory &factory,
                     Stash &stash);
};

} // namespace
