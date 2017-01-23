// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/gbdt.h>

#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/ExecutionEngine/ExecutionEngine.h>
#include <llvm/PassManager.h>
#include <mutex>

extern "C" {
    double vespalib_eval_ldexp(double a, double b);
    double vespalib_eval_min(double a, double b);
    double vespalib_eval_max(double a, double b);
    double vespalib_eval_isnan(double a);
    double vespalib_eval_approx(double a, double b);
    double vespalib_eval_relu(double a);
    double vespalib_eval_sigmoid(double a);
};

namespace vespalib {
namespace eval {

/**
 * Simple interface used to track and clean up custom state. This is
 * typically used to destruct native objects that are invoked from
 * within the generated machine code as part of evaluation. An example
 * is that large set membership checks against constant values will be
 * transformed into lookups in a pre-generated hash table.
 **/
struct PluginState {
    using UP = std::unique_ptr<PluginState>;
    virtual ~PluginState() {}
};

/**
 * Stuff related to LLVM code generation is wrapped in this
 * class. This is mostly used by the CompiledFunction class.
 **/
class LLVMWrapper
{
private:
    llvm::LLVMContext         *_context;
    llvm::Module              *_module; // owned by engine
    llvm::ExecutionEngine     *_engine;
    size_t                     _num_functions;
    std::vector<gbdt::Forest::UP> _forests;
    std::vector<PluginState::UP> _plugin_state;

    static std::recursive_mutex _global_llvm_lock;

public:
    LLVMWrapper();
    LLVMWrapper(LLVMWrapper &&rhs);
    void *compile_function(size_t num_params, bool use_array, const nodes::Node &root,
                           const gbdt::Optimize::Chain &forest_optimizers);
    void *compile_forest_fragment(const std::vector<const nodes::Node *> &fragment);
    const std::vector<gbdt::Forest::UP> &get_forests() const { return _forests; }
    void dump() const { _module->dump(); }
    ~LLVMWrapper();
};

} // namespace vespalib::eval
} // namespace vespalib

