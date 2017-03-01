// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "interpreted_function.h"
#include "node_visitor.h"
#include "node_traverser.h"
#include "check_type.h"
#include <cmath>
#include <vespa/vespalib/util/approx.h>
#include "operation.h"
#include <set>
#include "tensor_spec.h"
#include "simple_tensor_engine.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/vespalib/util/benchmark_timer.h>

namespace vespalib {
namespace eval {

namespace {

using namespace nodes;
using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;

//-----------------------------------------------------------------------------

template <typename T, typename IN>
uint64_t wrap_param(const IN &value_in) {
    const T &value = value_in;
    return (uint64_t)&value;
}

template <typename T>
const T &unwrap_param(uint64_t param) { return *((const T *)param); }

//-----------------------------------------------------------------------------

void op_load_const(State &state, uint64_t param) {
    state.stack.push_back(unwrap_param<Value>(param));
}

void op_load_param(State &state, uint64_t param) {
    state.stack.push_back(state.params->resolve(param, state.stash));
}

void op_load_let(State &state, uint64_t param) {
    state.stack.push_back(state.let_values[param]);
}

//-----------------------------------------------------------------------------

template <typename OP1>
void op_unary(State &state, uint64_t) {
    state.replace(1, OP1().perform(state.peek(0), state.stash));
}

template <typename OP2>
void op_binary(State &state, uint64_t) {
    state.replace(2, OP2().perform(state.peek(1), state.peek(0), state.stash));
}

//-----------------------------------------------------------------------------

void op_skip(State &state, uint64_t param) {
    state.program_offset += param;
}

void op_skip_if_false(State &state, uint64_t param) {
    ++state.if_cnt;
    if (!state.peek(0).as_bool()) {
        state.program_offset += param;
    }
    state.stack.pop_back();
}

//-----------------------------------------------------------------------------

void op_store_let(State &state, uint64_t) {
    state.let_values.push_back(state.peek(0));
    state.stack.pop_back();
}

void op_evict_let(State &state, uint64_t) {
    state.let_values.pop_back();
}

//-----------------------------------------------------------------------------

// compare lhs with a set member, short-circuit if found
void op_check_member(State &state, uint64_t param) {
    if (state.peek(1).equal(state.peek(0))) {
        state.replace(2, state.stash.create<DoubleValue>(1.0));
        state.program_offset += param;
    } else {
        state.stack.pop_back();
    }
}

// set member not found, replace lhs with false
void op_not_member(State &state, uint64_t) {
    state.stack.pop_back();
    state.stack.push_back(state.stash.create<DoubleValue>(0.0));
}

//-----------------------------------------------------------------------------

void op_tensor_sum(State &state, uint64_t) {
    const eval::Tensor *tensor = state.peek(0).as_tensor();
    if (tensor != nullptr) {
        state.replace(1, tensor->engine().reduce(*tensor, operation::Add(), {}, state.stash));
    }
}

void op_tensor_sum_dimension(State &state, uint64_t param) {
    const eval::Tensor *tensor = state.peek(0).as_tensor();
    if (tensor != nullptr) {
        const vespalib::string &dimension = unwrap_param<vespalib::string>(param);
        state.replace(1, tensor->engine().reduce(*tensor, operation::Add(), {dimension}, state.stash));
    } else {
        state.replace(1, state.stash.create<ErrorValue>());
    }
}

//-----------------------------------------------------------------------------

void op_tensor_map(State &state, uint64_t param) {
    const CompiledFunction &cfun = unwrap_param<CompiledFunction>(param);
    state.replace(1, state.engine.map(state.peek(0), cfun.get_function<1>(), state.stash));
}

void op_tensor_join(State &state, uint64_t param) {
    const CompiledFunction &cfun = unwrap_param<CompiledFunction>(param);
    state.replace(2, state.engine.join(state.peek(1), state.peek(0), cfun.get_function<2>(), state.stash));
}

using ReduceParams = std::pair<Aggr,std::vector<vespalib::string>>;
void op_tensor_reduce(State &state, uint64_t param) {
    const ReduceParams &params = unwrap_param<ReduceParams>(param);
    state.replace(1, state.engine.reduce(state.peek(0), params.first, params.second, state.stash));
}

using RenameParams = std::pair<std::vector<vespalib::string>,std::vector<vespalib::string>>;
void op_tensor_rename(State &state, uint64_t param) {
    const RenameParams &params = unwrap_param<RenameParams>(param);
    state.replace(1, state.engine.rename(state.peek(0), params.first, params.second, state.stash));
}

void op_tensor_concat(State &state, uint64_t param) {
    const vespalib::string &dimension = unwrap_param<vespalib::string>(param);
    state.replace(2, state.engine.concat(state.peek(1), state.peek(0), dimension, state.stash));
}

//-----------------------------------------------------------------------------

template <typename T>
const T &undef_cref() {   
    const T *undef = nullptr;
    assert(undef);
    return *undef;
}

struct TensorFunctionArgArgMeta {
    TensorFunction::UP function;
    size_t param1;
    size_t param2;
    TensorFunctionArgArgMeta(TensorFunction::UP function_in, size_t param1_in, size_t param2_in)
        : function(std::move(function_in)), param1(param1_in), param2(param2_in) {}
};

struct ArgArgInput : TensorFunction::Input {
    const TensorFunctionArgArgMeta &meta;
    State &state;
    ArgArgInput(const TensorFunctionArgArgMeta &meta_in, State &state_in)
        : meta(meta_in), state(state_in) {}
    const Value &get_tensor(size_t id) const override {
        if (id == 0) {
            return state.params->resolve(meta.param1, state.stash);
        } else if (id == 1) {
            return state.params->resolve(meta.param2, state.stash);
        }
        return undef_cref<Value>();
    }
    const UnaryOperation &get_map_operation(size_t) const override {
        return undef_cref<UnaryOperation>();
    }
};

void op_tensor_function_arg_arg(State &state, uint64_t param) {
    const TensorFunctionArgArgMeta &meta = unwrap_param<TensorFunctionArgArgMeta>(param);
    ArgArgInput input(meta, state);
    state.stack.push_back(meta.function->eval(input, state.stash));
}

//-----------------------------------------------------------------------------

bool step_labels(std::vector<double> &labels, const ValueType &type) {
    for (size_t idx = labels.size(); idx-- > 0; ) {
        labels[idx] += 1.0;
        if (size_t(labels[idx]) < type.dimensions()[idx].size) {
            return true;
        } else {
            labels[idx] = 0.0;
        }
    }
    return false;
}

//-----------------------------------------------------------------------------

struct ProgramBuilder : public NodeVisitor, public NodeTraverser {
    std::vector<Instruction> &program;
    Stash                    &stash;
    const TensorEngine       &tensor_engine;
    const NodeTypes          &types;

    ProgramBuilder(std::vector<Instruction> &program_in, Stash &stash_in, const TensorEngine &tensor_engine_in, const NodeTypes &types_in)
        : program(program_in), stash(stash_in), tensor_engine(tensor_engine_in), types(types_in) {}

    //-------------------------------------------------------------------------

    bool is_typed_tensor(const Node &node) const {
        const ValueType &type = types.get_type(node);
        return (type.is_tensor() && !type.dimensions().empty());
    }

    bool is_typed(const Node &node) const {
        return (types.get_type(node).is_double() || is_typed_tensor(node));
    }

    bool is_typed_tensor_param(const Node &node) const {
        auto sym = as<Symbol>(node);
        return (sym && (sym->id() >= 0) && is_typed_tensor(node));
    }

    bool is_typed_tensor_product_of_params(const Node &node) const {
        auto mul = as<Mul>(node);
        return (mul && is_typed_tensor(*mul) &&
                is_typed_tensor_param(mul->lhs()) &&
                is_typed_tensor_param(mul->rhs()));
    }

    //-------------------------------------------------------------------------

    virtual void visit(const Number &node) {
        program.emplace_back(op_load_const, wrap_param<Value>(stash.create<DoubleValue>(node.value())));
    }
    virtual void visit(const Symbol &node) {
        if (node.id() >= 0) { // param value
            program.emplace_back(op_load_param, node.id());
        } else { // let binding
            int let_offset = -(node.id() + 1);
            program.emplace_back(op_load_let, let_offset);
        }
    }
    virtual void visit(const String &node) {
        program.emplace_back(op_load_const, wrap_param<Value>(stash.create<DoubleValue>(node.hash())));
    }
    virtual void visit(const Array &node) {
        program.emplace_back(op_load_const, wrap_param<Value>(stash.create<DoubleValue>(node.size())));
    }
    virtual void visit(const Neg &) {
        program.emplace_back(op_unary<operation::Neg>);
    }
    virtual void visit(const Not &) {
        program.emplace_back(op_unary<operation::Not>);
    }
    virtual void visit(const If &node) {
        node.cond().traverse(*this);
        size_t after_cond = program.size();
        program.emplace_back(op_skip_if_false);
        node.true_expr().traverse(*this);
        size_t after_true = program.size();
        program.emplace_back(op_skip);
        node.false_expr().traverse(*this);
        program[after_cond].update_param(after_true - after_cond);
        program[after_true].update_param(program.size() - after_true - 1);
    }
    virtual void visit(const Let &node) {
        node.value().traverse(*this);
        program.emplace_back(op_store_let);
        node.expr().traverse(*this);
        program.emplace_back(op_evict_let);
    }
    virtual void visit(const Error &) {
        program.emplace_back(op_load_const, wrap_param<Value>(stash.create<ErrorValue>()));
    }
    virtual void visit(const TensorSum &node) {
        if (is_typed(node) && is_typed_tensor_product_of_params(node.get_child(0))) {
            assert(program.size() >= 3); // load,load,mul
            program.pop_back(); // mul
            program.pop_back(); // load
            program.pop_back(); // load
            std::vector<vespalib::string> dim_list;
            if (!node.dimension().empty()) {
                dim_list.push_back(node.dimension());
            }
            auto a = as<Symbol>(node.get_child(0).get_child(0));
            auto b = as<Symbol>(node.get_child(0).get_child(1));
            auto ir = tensor_function::reduce(tensor_function::apply(operation::Mul(),
                            tensor_function::inject(types.get_type(*a), 0),
                            tensor_function::inject(types.get_type(*b), 1)), operation::Add(), dim_list);
            auto fun = tensor_engine.compile(std::move(ir));
            const auto &meta = stash.create<TensorFunctionArgArgMeta>(std::move(fun), a->id(), b->id());
            program.emplace_back(op_tensor_function_arg_arg, wrap_param<TensorFunctionArgArgMeta>(meta));
        } else if (node.dimension().empty()) {
            program.emplace_back(op_tensor_sum);
        } else {
            program.emplace_back(op_tensor_sum_dimension,
                                 wrap_param<vespalib::string>(stash.create<vespalib::string>(node.dimension())));
        }
    }
    virtual void visit(const TensorMap &node) {
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
        program.emplace_back(op_tensor_map, wrap_param<CompiledFunction>(token.get()->get()));
    }
    virtual void visit(const TensorJoin &node) {
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
        program.emplace_back(op_tensor_join, wrap_param<CompiledFunction>(token.get()->get()));
    }
    virtual void visit(const TensorReduce &node) {
        ReduceParams &params = stash.create<ReduceParams>(node.aggr(), node.dimensions());
        program.emplace_back(op_tensor_reduce, wrap_param<ReduceParams>(params));
    }
    virtual void visit(const TensorRename &node) {
        RenameParams &params = stash.create<RenameParams>(node.from(), node.to());
        program.emplace_back(op_tensor_rename, wrap_param<RenameParams>(params));
    }
    virtual void visit(const TensorLambda &node) {
        const auto &type = node.type();
        TensorSpec spec(type.to_spec());
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::ARRAY));
        auto fun = token.get()->get().get_function();
        std::vector<double> params(type.dimensions().size(), 0.0);
        assert(token.get()->get().num_params() == params.size());
        do {
            TensorSpec::Address addr;
            for (size_t i = 0; i < params.size(); ++i) {
                addr.emplace(type.dimensions()[i].name, size_t(params[i]));
            }
            spec.add(addr, fun(&params[0]));
        } while (step_labels(params, type));
        auto tensor = tensor_engine.create(spec);
        program.emplace_back(op_load_const, wrap_param<Value>(stash.create<TensorValue>(std::move(tensor))));
    }
    virtual void visit(const TensorConcat &node) {
        vespalib::string &dimension = stash.create<vespalib::string>(node.dimension());
        program.emplace_back(op_tensor_concat, wrap_param<vespalib::string>(dimension));
    }
    virtual void visit(const Add &) {
        program.emplace_back(op_binary<operation::Add>);
    }
    virtual void visit(const Sub &) {
        program.emplace_back(op_binary<operation::Sub>);
    }
    virtual void visit(const Mul &) {
        program.emplace_back(op_binary<operation::Mul>);
    }
    virtual void visit(const Div &) {
        program.emplace_back(op_binary<operation::Div>);
    }
    virtual void visit(const Pow &) {
        program.emplace_back(op_binary<operation::Pow>);
    }
    virtual void visit(const Equal &) {
        program.emplace_back(op_binary<operation::Equal>);
    }
    virtual void visit(const NotEqual &) {
        program.emplace_back(op_binary<operation::NotEqual>);
    }
    virtual void visit(const Approx &) {
        program.emplace_back(op_binary<operation::Approx>);
    }
    virtual void visit(const Less &) {
        program.emplace_back(op_binary<operation::Less>);
    }
    virtual void visit(const LessEqual &) {
        program.emplace_back(op_binary<operation::LessEqual>);
    }
    virtual void visit(const Greater &) {
        program.emplace_back(op_binary<operation::Greater>);
    }
    virtual void visit(const GreaterEqual &) {
        program.emplace_back(op_binary<operation::GreaterEqual>);
    }
    virtual void visit(const In &node) {
        std::vector<size_t> checks;
        node.lhs().traverse(*this);
        auto array = as<Array>(node.rhs());
        if (array) {
            for (size_t i = 0; i < array->size(); ++i) {
                array->get(i).traverse(*this);
                checks.push_back(program.size());
                program.emplace_back(op_check_member);
            }
        } else {
            node.rhs().traverse(*this);
            checks.push_back(program.size());
            program.emplace_back(op_check_member);
        }
        for (size_t i = 0; i < checks.size(); ++i) {
            program[checks[i]].update_param(program.size() - checks[i]);
        }
        program.emplace_back(op_not_member);
    }
    virtual void visit(const And &) {
        program.emplace_back(op_binary<operation::And>);
    }
    virtual void visit(const Or &) {
        program.emplace_back(op_binary<operation::Or>);
    }
    virtual void visit(const Cos &) {
        program.emplace_back(op_unary<operation::Cos>);
    }
    virtual void visit(const Sin &) {
        program.emplace_back(op_unary<operation::Sin>);
    }
    virtual void visit(const Tan &) {
        program.emplace_back(op_unary<operation::Tan>);
    }
    virtual void visit(const Cosh &) {
        program.emplace_back(op_unary<operation::Cosh>);
    }
    virtual void visit(const Sinh &) {
        program.emplace_back(op_unary<operation::Sinh>);
    }
    virtual void visit(const Tanh &) {
        program.emplace_back(op_unary<operation::Tanh>);
    }
    virtual void visit(const Acos &) {
        program.emplace_back(op_unary<operation::Acos>);
    }
    virtual void visit(const Asin &) {
        program.emplace_back(op_unary<operation::Asin>);
    }
    virtual void visit(const Atan &) {
        program.emplace_back(op_unary<operation::Atan>);
    }
    virtual void visit(const Exp &) {
        program.emplace_back(op_unary<operation::Exp>);
    }
    virtual void visit(const Log10 &) {
        program.emplace_back(op_unary<operation::Log10>);
    }
    virtual void visit(const Log &) {
        program.emplace_back(op_unary<operation::Log>);
    }
    virtual void visit(const Sqrt &) {
        program.emplace_back(op_unary<operation::Sqrt>);
    }
    virtual void visit(const Ceil &) {
        program.emplace_back(op_unary<operation::Ceil>);
    }
    virtual void visit(const Fabs &) {
        program.emplace_back(op_unary<operation::Fabs>);
    }
    virtual void visit(const Floor &) {
        program.emplace_back(op_unary<operation::Floor>);
    }
    virtual void visit(const Atan2 &) {
        program.emplace_back(op_binary<operation::Atan2>);
    }
    virtual void visit(const Ldexp &) {
        program.emplace_back(op_binary<operation::Ldexp>);
    }
    virtual void visit(const Pow2 &) {
        program.emplace_back(op_binary<operation::Pow>);
    }
    virtual void visit(const Fmod &) {
        program.emplace_back(op_binary<operation::Fmod>);
    }
    virtual void visit(const Min &) {
        program.emplace_back(op_binary<operation::Min>);
    }
    virtual void visit(const Max &) {
        program.emplace_back(op_binary<operation::Max>);
    }
    virtual void visit(const IsNan &) {
        program.emplace_back(op_unary<operation::IsNan>);
    }
    virtual void visit(const Relu &) {
        program.emplace_back(op_unary<operation::Relu>);
    }
    virtual void visit(const Sigmoid &) {
        program.emplace_back(op_unary<operation::Sigmoid>);
    }

    //-------------------------------------------------------------------------

    virtual bool open(const Node &node) {
        if (check_type<Array, If, Let, In>(node)) {
            node.accept(*this);
            return false;
        }
        return true;
    }

    virtual void close(const Node &node) {
        node.accept(*this);
    }
};

const Function *get_lambda(const nodes::Node &node) {
    if (auto ptr = as<nodes::TensorMap>(node)) {
        return &ptr->lambda();
    }
    if (auto ptr = as<nodes::TensorJoin>(node)) {
        return &ptr->lambda();
    }
    if (auto ptr = as<nodes::TensorLambda>(node)) {
        return &ptr->lambda();
    }
    return nullptr;
}

} // namespace vespalib::<unnamed>


InterpretedFunction::LazyParams::~LazyParams()
{
}

const Value &
InterpretedFunction::SimpleParams::resolve(size_t idx, Stash &stash) const
{
    assert(idx < params.size());
    return stash.create<DoubleValue>(params[idx]);
}

const Value &
InterpretedFunction::SimpleObjectParams::resolve(size_t idx, Stash &) const
{
    assert(idx < params.size());
    return params[idx];
}

InterpretedFunction::State::State(const TensorEngine &engine_in)
    : engine(engine_in),
      params(nullptr),
      stash(),
      stack(),
      let_values(),
      program_offset(0)
{
}

void
InterpretedFunction::State::init(const LazyParams &params_in) {
    params = &params_in;
    stash.clear();
    stack.clear();
    let_values.clear();
    program_offset = 0;
    if_cnt = 0;
}

void
InterpretedFunction::State::replace(size_t prune_cnt, const Value &value) {
    for (size_t i = 0; i < prune_cnt; ++i) {
        stack.pop_back();
    }
    stack.push_back(value);
}

InterpretedFunction::Context::Context(const InterpretedFunction &ifun)
    : _state(ifun._tensor_engine)
{
}

InterpretedFunction::InterpretedFunction(const TensorEngine &engine, const nodes::Node &root, size_t num_params_in, const NodeTypes &types)
    : _program(),
      _stash(),
      _num_params(num_params_in),
      _tensor_engine(engine)
{
    ProgramBuilder program_builder(_program, _stash, _tensor_engine, types);
    root.traverse(program_builder);
}

const Value &
InterpretedFunction::eval(Context &ctx, const LazyParams &params) const
{
    State &state = ctx._state;
    state.init(params);
    while (state.program_offset < _program.size()) {
        _program[state.program_offset++].perform(state);
    }
    if (state.stack.size() != 1) {
        state.stack.push_back(state.stash.create<ErrorValue>());
    }
    return state.stack.back();
}

double
InterpretedFunction::estimate_cost_us(const std::vector<double> &params, double budget) const
{
    Context ctx(*this);
    SimpleParams lazy_params(params);
    auto actual = [&](){eval(ctx, lazy_params);};
    return BenchmarkTimer::benchmark(actual, budget) * 1000.0 * 1000.0;
}

Function::Issues
InterpretedFunction::detect_issues(const Function &function)
{
    struct NotSupported : NodeTraverser {
        std::vector<vespalib::string> issues;
        bool open(const nodes::Node &) override { return true; }
        void close(const nodes::Node &node) override {
            auto lambda = get_lambda(node);
            if (lambda && CompiledFunction::detect_issues(*lambda)) {
                issues.push_back(make_string("lambda function that cannot be compiled within %s",
                                getClassName(node).c_str()));
            }
        }
    } checker;
    function.root().traverse(checker);
    return Function::Issues(std::move(checker.issues));
}

} // namespace vespalib::eval
} // namespace vespalib
