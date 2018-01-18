// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "interpreted_function.h"
#include "node_visitor.h"
#include "node_traverser.h"
#include "check_type.h"
#include "tensor_spec.h"
#include "operation.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <set>

namespace vespalib {
namespace eval {

namespace {

using namespace nodes;
using State = InterpretedFunction::State;
using Instruction = InterpretedFunction::Instruction;
using map_fun_t = double (*)(double);
using join_fun_t = double (*)(double, double);

//-----------------------------------------------------------------------------

template <typename T, typename IN>
uint64_t wrap_param(const IN &value_in) {
    const T &value = value_in;
    return (uint64_t)&value;
}

template <typename T>
const T &unwrap_param(uint64_t param) { return *((const T *)param); }

//-----------------------------------------------------------------------------

uint64_t to_param(map_fun_t value) { return (uint64_t)value; }
uint64_t to_param(join_fun_t value) { return (uint64_t)value; }
map_fun_t to_map_fun(uint64_t param) { return (map_fun_t)param; }
join_fun_t to_join_fun(uint64_t param) { return (join_fun_t)param; }

//-----------------------------------------------------------------------------

void op_load_const(State &state, uint64_t param) {
    state.stack.push_back(unwrap_param<Value>(param));
}

void op_load_param(State &state, uint64_t param) {
    state.stack.push_back(state.params->resolve(param, state.stash));
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

void op_double_map(State &state, uint64_t param) {
    state.replace(1, state.stash.create<DoubleValue>(to_map_fun(param)(state.peek(0).as_double())));
}

void op_double_mul(State &state, uint64_t) {
    state.replace(2, state.stash.create<DoubleValue>(state.peek(1).as_double() * state.peek(0).as_double()));
}

void op_double_add(State &state, uint64_t) {
    state.replace(2, state.stash.create<DoubleValue>(state.peek(1).as_double() + state.peek(0).as_double()));
}

void op_double_join(State &state, uint64_t param) {
    state.replace(2, state.stash.create<DoubleValue>(to_join_fun(param)(state.peek(1).as_double(), state.peek(0).as_double())));
}

//-----------------------------------------------------------------------------

void op_tensor_map(State &state, uint64_t param) {
    state.replace(1, state.engine.map(state.peek(0), to_map_fun(param), state.stash));
}

void op_tensor_join(State &state, uint64_t param) {
    state.replace(2, state.engine.join(state.peek(1), state.peek(0), to_join_fun(param), state.stash));
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

void op_tensor_function(State &state, uint64_t param) {
    const TensorFunction &fun = unwrap_param<TensorFunction>(param);
    state.stack.push_back(fun.eval(state.engine, *state.params, state.stash));
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

    bool is_mul_join(const Node &node) const {
        if (auto join = as<TensorJoin>(node)) {
            if (auto mul = as<Mul>(join->lambda().root())) {
                auto sym1 = as<Symbol>(mul->lhs());
                auto sym2 = as<Symbol>(mul->rhs());
                return (sym1 && sym2 && (sym1->id() != sym2->id()));
            }
        }
        return false;
    }

    bool is_mul(const Node &node) const {
        auto mul = as<Mul>(node);
        return (mul || is_mul_join(node));
    }

    bool is_typed_tensor(const Node &node) const {
        const ValueType &type = types.get_type(node);
        return (type.is_tensor() && !type.dimensions().empty());
    }

    bool is_typed_tensor_param(const Node &node) const {
        auto sym = as<Symbol>(node);
        return (sym && is_typed_tensor(node));
    }

    bool is_typed_tensor_product_of_params(const Node &node) const {
        return (is_typed_tensor(node) && is_mul(node) &&
                is_typed_tensor_param(node.get_child(0)) &&
                is_typed_tensor_param(node.get_child(1)));
    }

    //-------------------------------------------------------------------------

    void make_const_op(const Node &node, const Value &value) {
        (void) node;
        program.emplace_back(op_load_const, wrap_param<Value>(value));
    }

    void make_map_op(const Node &node, map_fun_t function) {
        if (types.get_type(node).is_double()) {
            program.emplace_back(op_double_map, to_param(function));
        } else {
            program.emplace_back(op_tensor_map, to_param(function));
        }
    }

    void make_join_op(const Node &node, join_fun_t function) {
        if (types.get_type(node).is_double()) {
            if (function == operation::Mul::f) {
                program.emplace_back(op_double_mul);
            } else if (function == operation::Add::f) {
                program.emplace_back(op_double_add);
            } else {
                program.emplace_back(op_double_join, to_param(function));
            }
        } else {
            program.emplace_back(op_tensor_join, to_param(function));
        }
    }

    //-------------------------------------------------------------------------

    void visit(const Number &node) override {
        make_const_op(node, stash.create<DoubleValue>(node.value()));
    }
    void visit(const Symbol &node) override {
        program.emplace_back(op_load_param, node.id());
    }
    void visit(const String &node) override {
        make_const_op(node, stash.create<DoubleValue>(node.hash()));
    }
    void visit(const In &node) override {
        auto my_in = std::make_unique<In>(std::make_unique<Symbol>(0));
        for (size_t i = 0; i < node.num_entries(); ++i) {
            my_in->add_entry(std::make_unique<Number>(node.get_entry(i).get_const_value()));
        }
        Function my_fun(std::move(my_in), {"x"});
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(my_fun, PassParams::SEPARATE));
        make_map_op(node, token.get()->get().get_function<1>());
    }
    void visit(const Neg &node) override {
        make_map_op(node, operation::Neg::f);
    }
    void visit(const Not &node) override {
        make_map_op(node, operation::Not::f);
    }
    void visit(const If &node) override {
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
    void visit(const Error &node) override {
        make_const_op(node, ErrorValue::instance);
    }
    void visit(const TensorMap &node) override {
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
        make_map_op(node, token.get()->get().get_function<1>());
    }
    void visit(const TensorJoin &node) override {
        const auto &token = stash.create<CompileCache::Token::UP>(CompileCache::compile(node.lambda(), PassParams::SEPARATE));
        make_join_op(node, token.get()->get().get_function<2>());
    }
    void visit(const TensorReduce &node) override {
        if ((node.aggr() == Aggr::SUM) && is_typed_tensor_product_of_params(node.get_child(0))) {
            assert(program.size() >= 3); // load,load,mul
            program.pop_back(); // mul
            program.pop_back(); // load
            program.pop_back(); // load
            auto a = as<Symbol>(node.get_child(0).get_child(0));
            auto b = as<Symbol>(node.get_child(0).get_child(1));
            const auto &ir = tensor_function::reduce(tensor_function::join(
                            tensor_function::inject(types.get_type(*a), a->id(), stash),
                            tensor_function::inject(types.get_type(*b), b->id(), stash),
                            operation::Mul::f, stash), node.aggr(), node.dimensions(), stash);
            const auto &fun = tensor_engine.compile(ir, stash);
            program.emplace_back(op_tensor_function, wrap_param<TensorFunction>(fun));
        } else {
            ReduceParams &params = stash.create<ReduceParams>(node.aggr(), node.dimensions());
            program.emplace_back(op_tensor_reduce, wrap_param<ReduceParams>(params));
        }
    }
    void visit(const TensorRename &node) override {
        RenameParams &params = stash.create<RenameParams>(node.from(), node.to());
        program.emplace_back(op_tensor_rename, wrap_param<RenameParams>(params));
    }
    void visit(const TensorLambda &node) override {
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
        make_const_op(node, *stash.create<Value::UP>(tensor_engine.from_spec(spec)));
    }
    void visit(const TensorConcat &node) override {
        vespalib::string &dimension = stash.create<vespalib::string>(node.dimension());
        program.emplace_back(op_tensor_concat, wrap_param<vespalib::string>(dimension));
    }
    void visit(const Add &node) override {
        make_join_op(node, operation::Add::f);
    }
    void visit(const Sub &node) override {
        make_join_op(node, operation::Sub::f);
    }
    void visit(const Mul &node) override {
        make_join_op(node, operation::Mul::f);
    }
    void visit(const Div &node) override {
        make_join_op(node, operation::Div::f);
    }
    void visit(const Mod &node) override {
        make_join_op(node, operation::Mod::f);
    }
    void visit(const Pow &node) override {
        make_join_op(node, operation::Pow::f);
    }
    void visit(const Equal &node) override {
        make_join_op(node, operation::Equal::f);
    }
    void visit(const NotEqual &node) override {
        make_join_op(node, operation::NotEqual::f);
    }
    void visit(const Approx &node) override {
        make_join_op(node, operation::Approx::f);
    }
    void visit(const Less &node) override {
        make_join_op(node, operation::Less::f);
    }
    void visit(const LessEqual &node) override {
        make_join_op(node, operation::LessEqual::f);
    }
    void visit(const Greater &node) override {
        make_join_op(node, operation::Greater::f);
    }
    void visit(const GreaterEqual &node) override {
        make_join_op(node, operation::GreaterEqual::f);
    }
    void visit(const And &node) override {
        make_join_op(node, operation::And::f);
    }
    void visit(const Or &node) override {
        make_join_op(node, operation::Or::f);
    }
    void visit(const Cos &node) override {
        make_map_op(node, operation::Cos::f);
    }
    void visit(const Sin &node) override {
        make_map_op(node, operation::Sin::f);
    }
    void visit(const Tan &node) override {
        make_map_op(node, operation::Tan::f);
    }
    void visit(const Cosh &node) override {
        make_map_op(node, operation::Cosh::f);
    }
    void visit(const Sinh &node) override {
        make_map_op(node, operation::Sinh::f);
    }
    void visit(const Tanh &node) override {
        make_map_op(node, operation::Tanh::f);
    }
    void visit(const Acos &node) override {
        make_map_op(node, operation::Acos::f);
    }
    void visit(const Asin &node) override {
        make_map_op(node, operation::Asin::f);
    }
    void visit(const Atan &node) override {
        make_map_op(node, operation::Atan::f);
    }
    void visit(const Exp &node) override {
        make_map_op(node, operation::Exp::f);
    }
    void visit(const Log10 &node) override {
        make_map_op(node, operation::Log10::f);
    }
    void visit(const Log &node) override {
        make_map_op(node, operation::Log::f);
    }
    void visit(const Sqrt &node) override {
        make_map_op(node, operation::Sqrt::f);
    }
    void visit(const Ceil &node) override {
        make_map_op(node, operation::Ceil::f);
    }
    void visit(const Fabs &node) override {
        make_map_op(node, operation::Fabs::f);
    }
    void visit(const Floor &node) override {
        make_map_op(node, operation::Floor::f);
    }
    void visit(const Atan2 &node) override {
        make_join_op(node, operation::Atan2::f);
    }
    void visit(const Ldexp &node) override {
        make_join_op(node, operation::Ldexp::f);
    }
    void visit(const Pow2 &node) override {
        make_join_op(node, operation::Pow::f);
    }
    void visit(const Fmod &node) override {
        make_join_op(node, operation::Mod::f);
    }
    void visit(const Min &node) override {
        make_join_op(node, operation::Min::f);
    }
    void visit(const Max &node) override {
        make_join_op(node, operation::Max::f);
    }
    void visit(const IsNan &node) override {
        make_map_op(node, operation::IsNan::f);
    }
    void visit(const Relu &node) override {
        make_map_op(node, operation::Relu::f);
    }
    void visit(const Sigmoid &node) override {
        make_map_op(node, operation::Sigmoid::f);
    }
    void visit(const Elu &node) override {
        make_map_op(node, operation::Elu::f);
    }

    //-------------------------------------------------------------------------

    bool open(const Node &node) override {
        if (check_type<If>(node)) {
            node.accept(*this);
            return false;
        }
        return true;
    }

    void close(const Node &node) override {
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


InterpretedFunction::State::State(const TensorEngine &engine_in)
    : engine(engine_in),
      params(nullptr),
      stash(),
      stack(),
      program_offset(0)
{
}

InterpretedFunction::State::~State() {}

void
InterpretedFunction::State::init(const LazyParams &params_in) {
    params = &params_in;
    stash.clear();
    stack.clear();
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

InterpretedFunction::~InterpretedFunction() {}

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
