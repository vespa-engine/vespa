// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/interpreted_function.h>

namespace vespalib { class Stash; }
namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

//-----------------------------------------------------------------------------

struct GenericCreate {
    static InterpretedFunction::Instruction
    make_instruction(const std::vector<TensorSpec::Address> &addresses,
                     const ValueType &res_type,
                     const ValueBuilderFactory &factory,
                     Stash &stash);
};

} // namespace
