// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "interpreted_function.h"
#include <vector>

namespace vespalib { class Stash; }

namespace vespalib::eval {

struct ValueBuilderFactory;
struct TensorFunction;

/**
 * Meta-data related to the compilation of a tensor function that may
 * be optionally collected. Each tensor function tree node will be
 * represented by a single 'Step' containing the class name of the
 * corresponding tree node and the symbol name of the low-level
 * function it compiles to. Steps are ordered according to the
 * instructions of the final program. Note that each 'If' node will
 * produce 2 steps; one for the conditional jump after the 'if'
 * condition has been calculated and one for the unconditional jump
 * after the 'true' branch.
 **/
struct CTFMetaData {
    struct Step {
        vespalib::string class_name;
        vespalib::string symbol_name;
        Step(vespalib::string &&class_name_in,
             vespalib::string &&symbol_name_in) noexcept
            : class_name(std::move(class_name_in)),
              symbol_name(std::move(symbol_name_in))
        {
        }
    };
    std::vector<Step> steps;
    ~CTFMetaData();
};

std::vector<InterpretedFunction::Instruction> compile_tensor_function(const ValueBuilderFactory &factory, const TensorFunction &function, Stash &stash,
                                                                      CTFMetaData *meta);

} // namespace vespalib::eval
