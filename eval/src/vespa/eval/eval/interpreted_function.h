// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "function.h"
#include <vespa/vespalib/util/stash.h>
#include "simple_tensor_engine.h"
#include "node_types.h"

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
        std::vector<Value::CREF> params;
        Stash                    stash;
        std::vector<Value::CREF> stack;
        std::vector<Value::CREF> let_values;
        uint32_t                 program_offset;
        uint32_t                 if_cnt;
        State() : params(), stash(), stack(), let_values(), program_offset(0) {}
        void clear() {
            stash.clear();
            stack.clear();
            let_values.clear();
            program_offset = 0;
            if_cnt = 0;
        }
        const Value &peek(size_t ridx) const {
            return stack[stack.size() - 1 - ridx];
        }
        void replace(size_t prune_cnt, const Value &value) {
            for (size_t i = 0; i < prune_cnt; ++i) {
                stack.pop_back();
            }
            stack.push_back(value);
        }
    };
    class Context {
        friend class InterpretedFunction;
    private:
        State _state;
        Stash _param_stash;
    public:
        void clear_params() {
            _state.params.clear();
            _param_stash.clear();
        }
        void add_param(const Value &param) { _state.params.push_back(param); }
        void add_param(double param) { add_param(_param_stash.create<DoubleValue>(param)); }
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
    size_t program_size() const { return _program.size(); }
    size_t num_params() const { return _num_params; }
    const Value &eval(Context &ctx) const;
    static Function::Issues detect_issues(const Function &function);
};

} // namespace vespalib::eval
} // namespace vespalib
