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
        std::unique_ptr<CTFMetaData> nested;
        Step(vespalib::string &&class_name_in,
             vespalib::string &&symbol_name_in) noexcept
          : class_name(std::move(class_name_in)),
            symbol_name(std::move(symbol_name_in)),
            nested()
        {
        }
    };
    std::vector<Step> steps;
    CTFMetaData() noexcept = default;
    CTFMetaData(const CTFMetaData &) = delete;
    CTFMetaData(CTFMetaData &&) noexcept = default;
    CTFMetaData &operator=(const CTFMetaData &) = delete;
    CTFMetaData &operator=(CTFMetaData &&) noexcept = default;
    std::unique_ptr<CTFMetaData> extract() {
        return steps.empty()
            ? std::unique_ptr<CTFMetaData>(nullptr)
            : std::make_unique<CTFMetaData>(std::move(*this));
    }
    ~CTFMetaData();
};

struct CTFContext {
    const ValueBuilderFactory &factory;
    Stash &stash;
    CTFMetaData *meta;
    constexpr CTFContext(const CTFContext &) noexcept = default;
    constexpr CTFContext(const ValueBuilderFactory &factory_in, Stash &stash_in, CTFMetaData *meta_in) noexcept
      : factory(factory_in), stash(stash_in), meta(meta_in) {}
};

std::vector<InterpretedFunction::Instruction> compile_tensor_function(const TensorFunction &function, const CTFContext &ctx);

} // namespace vespalib::eval
