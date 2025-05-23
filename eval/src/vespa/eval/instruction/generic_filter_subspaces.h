// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/node_types.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib::eval::instruction {

struct GenericFilterSubspaces {
    static InterpretedFunction::Instruction
    make_instruction(const ValueType &result_type,
                     const ValueType &inner_type,
                     const Function &lambda, const NodeTypes &types,
                     const ValueBuilderFactory &factory, Stash &stash);
};

} // namespace
