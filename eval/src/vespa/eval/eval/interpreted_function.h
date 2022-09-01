// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "function.h"
#include "node_types.h"
#include "lazy_params.h"
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/time.h>

namespace vespalib::eval {

namespace nodes { struct Node; }
struct TensorFunction;
class TensorSpec;
struct CTFMetaData;
struct ValueBuilderFactory;

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
        const ValueBuilderFactory &factory;
        const LazyParams          *params;
        Stash                      stash;
        std::vector<Value::CREF>   stack;
        uint32_t                   program_offset;
        uint32_t                   if_cnt;

        State(const ValueBuilderFactory &factory_in);
        ~State();

        void init(const LazyParams &params_in);
        const Value &peek(size_t ridx) const {
            return stack[stack.size() - 1 - ridx];
        }
        void pop_push(const Value &value) {
            stack.back() = value;
        }
        void pop_pop_push(const Value &value) {
            stack.pop_back();
            stack.back() = value;
        }
        void pop_pop_pop_push(const Value &value) {
            stack.pop_back();
            stack.pop_back();
            stack.back() = value;
        }
        void pop_n_push(size_t n, const Value &value) {
            stack.resize(stack.size() - (n - 1), value);
            stack.back() = value;
        }
    };
    class Context {
        friend class InterpretedFunction;
    private:
        State _state;
    public:
        explicit Context(const InterpretedFunction &ifun);
        uint32_t if_cnt() const { return _state.if_cnt; }
    };
    struct ProfiledContext {
        Context context;
        std::vector<std::pair<size_t,duration>> cost;
        ProfiledContext(const InterpretedFunction &ifun);
    };
    using op_function = void (*)(State &, uint64_t);
    class Instruction {
    private:
        op_function function;
        uint64_t    param;
    public:
        explicit Instruction(op_function function_in) noexcept
            : function(function_in), param(0) {}
        Instruction(op_function function_in, uint64_t param_in) noexcept
            : function(function_in), param(param_in) {}
        vespalib::string resolve_symbol() const;
        void perform(State &state) const {
            if (function == nullptr) {
                state.stack.push_back(state.params->resolve(param, state.stash));
            } else {
                function(state, param);
            }
        }
        static Instruction fetch_param(size_t param_idx) {
            return Instruction(nullptr, param_idx);
        }
        static Instruction nop();
    };

private:
    std::vector<Instruction>   _program;
    Stash                      _stash;
    const ValueBuilderFactory &_factory;

public:
    typedef std::unique_ptr<InterpretedFunction> UP;
    // for testing; use with care; the tensor function must be kept alive
    InterpretedFunction(const ValueBuilderFactory &factory, const TensorFunction &function, CTFMetaData *meta);
    InterpretedFunction(const ValueBuilderFactory &factory, const TensorFunction &function)
      : InterpretedFunction(factory, function, nullptr) {}
    InterpretedFunction(const ValueBuilderFactory &factory, const nodes::Node &root, const NodeTypes &types);
    InterpretedFunction(const ValueBuilderFactory &factory, const Function &function, const NodeTypes &types)
        : InterpretedFunction(factory, function.root(), types) {}
    InterpretedFunction(InterpretedFunction &&rhs) = default;
    ~InterpretedFunction();
    size_t program_size() const { return _program.size(); }
    const Value &eval(Context &ctx, const LazyParams &params) const;
    const Value &eval(ProfiledContext &ctx, const LazyParams &params) const;
    double estimate_cost_us(const std::vector<double> &params, double budget = 5.0) const;
    static Function::Issues detect_issues(const Function &function);

    /**
     * This inner class is used for testing and benchmarking. It runs
     * a single interpreted instruction in isolation. Note that
     * instructions manipulating the program counter may not be run in
     * this way. Also note that the stack must contain exactly one
     * value after the instruction is executed. The params object must
     * be kept alive externally.
     **/
    class EvalSingle {
    private:
        State _state;
        Instruction _op;
    public:
        EvalSingle(const ValueBuilderFactory &factory, Instruction op, const LazyParams &params);
        EvalSingle(const ValueBuilderFactory &factory, Instruction op) : EvalSingle(factory, op, NoParams::params) {}
        const Value &eval(const std::vector<Value::CREF> &stack);
    };
};

}
