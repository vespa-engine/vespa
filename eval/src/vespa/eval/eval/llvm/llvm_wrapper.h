// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/gbdt.h>

#include <llvm/IR/LLVMContext.h>
#include <llvm/IR/Module.h>
#include <llvm/ExecutionEngine/ExecutionEngine.h>
#include <mutex>

extern "C" {
    double vespalib_eval_ldexp(double a, double b);
    double vespalib_eval_min(double a, double b);
    double vespalib_eval_max(double a, double b);
    double vespalib_eval_isnan(double a);
    double vespalib_eval_approx(double a, double b);
    double vespalib_eval_relu(double a);
    double vespalib_eval_sigmoid(double a);
    double vespalib_eval_elu(double a);
    double vespalib_eval_bit(double a, double b);
    double vespalib_eval_hamming(double a, double b);
};

namespace vespalib::eval {

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
    std::unique_ptr<llvm::LLVMContext>     _context;
    std::unique_ptr<llvm::Module>          _module;
    std::unique_ptr<llvm::ExecutionEngine> _engine;
    std::vector<llvm::Function*>           _functions;
    std::vector<gbdt::Forest::UP>          _forests;
    std::vector<PluginState::UP>           _plugin_state;

    void compile(llvm::raw_ostream * dumpStream);
public:
    LLVMWrapper();
    LLVMWrapper(LLVMWrapper &&rhs) = default;

    size_t make_function(size_t num_params, PassParams pass_params, const nodes::Node &root,
                         const gbdt::Optimize::Chain &forest_optimizers);
    size_t make_forest_fragment(size_t num_params, const std::vector<const nodes::Node *> &fragment);
    const std::vector<gbdt::Forest::UP> &get_forests() const { return _forests; }
    void compile(llvm::raw_ostream & dumpStream) { compile(&dumpStream); }
    void compile() { compile(nullptr); }
    void *get_function_address(size_t function_id);
    ~LLVMWrapper();
};

}

