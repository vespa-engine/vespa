// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "interpreted_function.h"
#include "node_visitor.h"
#include "node_traverser.h"
#include "tensor_nodes.h"
#include "make_tensor_function.h"
#include "optimize_tensor_function.h"
#include "compile_tensor_function.h"
#include <vespa/vespalib/util/classname.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/eval/eval/llvm/addr_to_symbol.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <set>

namespace vespalib::eval {

namespace {

const Function *get_lambda(const nodes::Node &node) {
    if (auto ptr = nodes::as<nodes::TensorMap>(node)) {
        return &ptr->lambda();
    }
    if (auto ptr = nodes::as<nodes::TensorJoin>(node)) {
        return &ptr->lambda();
    }
    if (auto ptr = nodes::as<nodes::TensorMerge>(node)) {
        return &ptr->lambda();
    }
    return nullptr;
}

void my_nop(InterpretedFunction::State &, uint64_t) {}

} // namespace vespalib::<unnamed>


InterpretedFunction::State::State(const ValueBuilderFactory &factory_in)
    : factory(factory_in),
      params(nullptr),
      stash(),
      stack(),
      program_offset(0),
      if_cnt(0)
{
}

InterpretedFunction::State::~State() = default;

void
InterpretedFunction::State::init(const LazyParams &params_in) {
    params = &params_in;
    stash.clear();
    stack.clear();
    program_offset = 0;
    if_cnt = 0;
}

InterpretedFunction::Context::Context(const InterpretedFunction &ifun)
  : _state(ifun._factory)
{
}

InterpretedFunction::ProfiledContext::ProfiledContext(const InterpretedFunction &ifun)
  : context(ifun),
    cost(ifun.program_size(), std::make_pair(size_t(0), duration::zero()))
{
}

vespalib::string
InterpretedFunction::Instruction::resolve_symbol() const
{
    if (function == nullptr) {
        return "<inject_param>";
    }
    return addr_to_symbol((const void *)function);
}

InterpretedFunction::Instruction
InterpretedFunction::Instruction::nop()
{
    return Instruction(my_nop);
}

InterpretedFunction::InterpretedFunction(const ValueBuilderFactory &factory, const TensorFunction &function, CTFMetaData *meta)
    : _program(),
      _stash(),
      _factory(factory)
{
    _program = compile_tensor_function(factory, function, _stash, meta);
}

InterpretedFunction::InterpretedFunction(const ValueBuilderFactory &factory, const nodes::Node &root, const NodeTypes &types)
    : _program(),
      _stash(),
      _factory(factory)
{
    const TensorFunction &plain_fun = make_tensor_function(factory, root, types, _stash);
    const TensorFunction &optimized = optimize_tensor_function(factory, plain_fun, _stash);
    _program = compile_tensor_function(factory, optimized, _stash, nullptr);
}

InterpretedFunction::~InterpretedFunction() = default;

const Value &
InterpretedFunction::eval(Context &ctx, const LazyParams &params) const
{
    State &state = ctx._state;
    state.init(params);
    while (state.program_offset < _program.size()) {
        _program[state.program_offset++].perform(state);
    }
    assert(state.stack.size() == 1);
    return state.stack.back();
}

const Value &
InterpretedFunction::eval(ProfiledContext &pctx, const LazyParams &params) const
{
    auto &ctx = pctx.context;                            // Profiling
    State &state = ctx._state;
    state.init(params);
    while (state.program_offset < _program.size()) {
        auto pos = state.program_offset;                 // Profiling
        auto before = steady_clock::now();               // Profiling
        _program[state.program_offset++].perform(state);
        auto after = steady_clock::now();                // Profiling
        ++pctx.cost[pos].first;                          // Profiling
        pctx.cost[pos].second += (after - before);       // Profiling
    }
    assert(state.stack.size() == 1);
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

InterpretedFunction::EvalSingle::EvalSingle(const ValueBuilderFactory &factory, Instruction op, const LazyParams &params)
    : _state(factory),
      _op(op)
{
    _state.params = &params;
}

const Value &
InterpretedFunction::EvalSingle::eval(const std::vector<Value::CREF> &stack)
{
    _state.stash.clear();
    _state.stack = stack;
    _op.perform(_state);
    assert(_state.stack.size() == 1);
    return _state.stack.back();
}

}
