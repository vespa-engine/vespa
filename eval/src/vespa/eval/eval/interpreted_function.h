// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "function.h"
#include "simple_tensor_engine.h"
#include "node_types.h"
#include "lazy_params.h"
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace eval {

namespace nodes { class Node; }
class TensorEngine;

/**
 * A Function that has been prepared for execution. This will
 * typically run slower than a compiled function but faster than
 * evaluating the Function AST directly. The
 * InterpretedFunction::Context class is used to keep track of the
 * run-time state related to the evaluation of an interpreted
 * function. The result of an evaluation is only valid until either
 * the context is destructed or the context is re-used to perform
 * another evaluation.
 **/
class InterpretedFunction
{
public:
    struct State {
        const TensorEngine      &engine;
        const LazyParams        *params;
        Stash                    stash;
        std::vector<Value::CREF> stack;
        uint32_t                 program_offset;
        uint32_t                 if_cnt;

        State(const TensorEngine &engine_in);
        ~State();

        void init(const LazyParams &params_in);
        const Value &peek(size_t ridx) const {
            return stack[stack.size() - 1 - ridx];
        }
        void replace(size_t prune_cnt, const Value &value);
    };
    class Context {
        friend class InterpretedFunction;
    private:
        State _state;
    public:
        explicit Context(const InterpretedFunction &ifun);
        uint32_t if_cnt() const { return _state.if_cnt; }
    };
    using op_function = void (*)(State &, uint64_t);
    class Instruction {
    private:
        op_function function;
        uint64_t    param;
    public:
        explicit Instruction(op_function function_in)
            : function(function_in), param(0) {}
        Instruction(op_function function_in, uint64_t param_in)
            : function(function_in), param(param_in) {}
        void update_param(uint64_t param_in) { param = param_in; }
        void perform(State &state) const { function(state, param); }
    };

private:
    std::vector<Instruction> _program;
    Stash                    _stash;
    size_t                   _num_params;
    const TensorEngine      &_tensor_engine;

public:
    typedef std::unique_ptr<InterpretedFunction> UP;
    InterpretedFunction(const TensorEngine &engine, const nodes::Node &root, size_t num_params_in, const NodeTypes &types);
    InterpretedFunction(const TensorEngine &engine, const Function &function, const NodeTypes &types)
        : InterpretedFunction(engine, function.root(), function.num_params(), types) {}
    InterpretedFunction(InterpretedFunction &&rhs) = default;
    ~InterpretedFunction();
    size_t program_size() const { return _program.size(); }
    size_t num_params() const { return _num_params; }
    const Value &eval(Context &ctx, const LazyParams &params) const;
    double estimate_cost_us(const std::vector<double> &params, double budget = 5.0) const;
    static Function::Issues detect_issues(const Function &function);
};

} // namespace vespalib::eval
} // namespace vespalib
