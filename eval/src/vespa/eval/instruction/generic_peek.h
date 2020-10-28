// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <map>

namespace vespalib { class Stash; }
namespace vespalib::eval { struct ValueBuilderFactory; }

namespace vespalib::eval::instruction {

//-----------------------------------------------------------------------------

struct GenericPeek {
    using MyLabel = std::variant<TensorSpec::Label, size_t>;
    using SpecMap = std::map<vespalib::string, MyLabel>;

    static InterpretedFunction::Instruction
    make_instruction(const ValueType &input_type,
                     const ValueType &res_type,
                     const SpecMap &spec,
                     const ValueBuilderFactory &factory,
                     Stash &stash);
};

} // namespace
