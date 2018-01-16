// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rankingexpressionfeature.h"
#include "utils.h"
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/features/rankingexpression/feature_name_extractor.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/eval/param_usage.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.rankingexpression");

using vespalib::eval::Function;
using vespalib::eval::PassParams;
using vespalib::eval::CompileCache;
using vespalib::eval::CompiledFunction;
using vespalib::eval::InterpretedFunction;
using vespalib::eval::LazyParams;
using vespalib::eval::ValueType;
using vespalib::eval::Value;
using vespalib::eval::DoubleValue;
using vespalib::eval::NodeTypes;
using vespalib::tensor::DefaultTensorEngine;
using search::fef::FeatureType;
using vespalib::ArrayRef;
using vespalib::ConstArrayRef;

namespace search {
namespace features {

namespace {

vespalib::string list_issues(const std::vector<vespalib::string> &issues) {
    vespalib::string result;
    for (const auto &issue: issues) {
        result += vespalib::make_string("  issue: %s\n", issue.c_str());
    }
    return result;
}

} // namespace search::features::<unnamed>

//-----------------------------------------------------------------------------

/**
 * Implements the executor for compiled ranking expressions
 **/
class CompiledRankingExpressionExecutor : public fef::FeatureExecutor
{
private:
    typedef double (*arr_function)(const double *);
    arr_function _ranking_function;
    std::vector<double> _params;

public:
    CompiledRankingExpressionExecutor(const CompiledFunction &compiled_function);
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

/**
 * Implements the executor for lazy compiled ranking expressions
 **/
class LazyCompiledRankingExpressionExecutor : public fef::FeatureExecutor
{
private:
    using function_type = CompiledFunction::lazy_function;
    function_type _ranking_function;

public:
    LazyCompiledRankingExpressionExecutor(const CompiledFunction &compiled_function);
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

struct MyLazyParams : LazyParams {
    const fef::FeatureExecutor::Inputs &inputs;
    const ConstArrayRef<char> input_is_object;
    MyLazyParams(const fef::FeatureExecutor::Inputs &inputs_in, ConstArrayRef<char> input_is_object_in)
        : inputs(inputs_in), input_is_object(input_is_object_in) {}
    const Value &resolve(size_t idx, vespalib::Stash &stash) const override {
        if (input_is_object[idx]) {
            return inputs.get_object(idx);
        } else {
            return stash.create<DoubleValue>(inputs.get_number(idx));
        }
    }
};

/**
 * Implements the executor for interpreted ranking expressions (with tensor support)
 **/
class InterpretedRankingExpressionExecutor : public fef::FeatureExecutor
{
private:
    const InterpretedFunction   &_function;
    InterpretedFunction::Context _context;
    MyLazyParams                 _params;

public:
    InterpretedRankingExpressionExecutor(const InterpretedFunction &function,
                                         ConstArrayRef<char> input_is_object);
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

CompiledRankingExpressionExecutor::CompiledRankingExpressionExecutor(const CompiledFunction &compiled_function)
    : _ranking_function(compiled_function.get_function()),
      _params(compiled_function.num_params(), 0.0)
{
}

void
CompiledRankingExpressionExecutor::execute(uint32_t)
{
    size_t i(0);
    for (; (i + 4) < _params.size(); i += 4) {
        _params[i+0] = inputs().get_number(i+0);
        _params[i+1] = inputs().get_number(i+1);
        _params[i+2] = inputs().get_number(i+2);
        _params[i+3] = inputs().get_number(i+3);
    }
    for (; i < _params.size(); ++i) {
        _params[i] = inputs().get_number(i);
    }
    outputs().set_number(0, _ranking_function(&_params[0]));
}

//-----------------------------------------------------------------------------

using Context = fef::FeatureExecutor::Inputs;
double resolve_input(void *ctx, size_t idx) { return ((const Context *)(ctx))->get_number(idx); }
Context *make_ctx(const Context &inputs) { return const_cast<Context *>(&inputs); }

LazyCompiledRankingExpressionExecutor::LazyCompiledRankingExpressionExecutor(const CompiledFunction &compiled_function)
    : _ranking_function(compiled_function.get_lazy_function())
{
}

void
LazyCompiledRankingExpressionExecutor::execute(uint32_t)
{
    outputs().set_number(0, _ranking_function(resolve_input, make_ctx(inputs())));
}

//-----------------------------------------------------------------------------

InterpretedRankingExpressionExecutor::InterpretedRankingExpressionExecutor(const InterpretedFunction &function,
                                                                           ConstArrayRef<char> input_is_object)
    : _function(function),
      _context(function),
      _params(inputs(), input_is_object)
{
}

void
InterpretedRankingExpressionExecutor::execute(uint32_t)
{
    outputs().set_object(0, _function.eval(_context, _params));
}

//-----------------------------------------------------------------------------

RankingExpressionBlueprint::RankingExpressionBlueprint()
    : RankingExpressionBlueprint(std::make_shared<rankingexpression::NullExpressionReplacer>()) {}

RankingExpressionBlueprint::RankingExpressionBlueprint(rankingexpression::ExpressionReplacer::SP replacer)
    : fef::Blueprint("rankingExpression"),
      _expression_replacer(std::move(replacer)),
      _intrinsic_expression(),
      _interpreted_function(),
      _compile_token(),
      _input_is_object()
{
}

RankingExpressionBlueprint::~RankingExpressionBlueprint()
{
}

void
RankingExpressionBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &,
                                              fef::IDumpFeatureVisitor &) const
{
}

bool
RankingExpressionBlueprint::setup(const fef::IIndexEnvironment &env,
                                  const fef::ParameterList &params)
{
    // Retrieve and concatenate whatever config is available.
    vespalib::string script = "";
    fef::Property property = env.getProperties().lookup(getName(), "rankingScript");
    if (property.size() > 0) {
        for (uint32_t i = 0; i < property.size(); ++i) {
            script.append(property.getAt(i));
        }
        //LOG(debug, "Script from config: '%s'\n", script.c_str());
    } else if (params.size() == 1) {
        script = params[0].getValue();
        //LOG(debug, "Script from param: '%s'\n", script.c_str());
    } else {
        LOG(error, "No expression given.");
        return false;
    }
    Function rank_function = Function::parse(script, rankingexpression::FeatureNameExtractor());
    if (rank_function.has_error()) {
        LOG(error, "Failed to parse expression '%s': %s", script.c_str(), rank_function.get_error().c_str());
        return false;
    }
    _intrinsic_expression = _expression_replacer->maybe_replace(rank_function, env);
    if (_intrinsic_expression) {
        LOG(info, "%s replaced with %s", getName().c_str(), _intrinsic_expression->describe_self().c_str());
        describeOutput("out", "result of intrinsic expression", _intrinsic_expression->result_type());
        return true;
    }
    bool do_compile = true;
    std::vector<ValueType> input_types;
    for (size_t i = 0; i < rank_function.num_params(); ++i) {
        const FeatureType &input = defineInput(rank_function.param_name(i), AcceptInput::ANY);
        _input_is_object.push_back(char(input.is_object()));
        if (input.is_object()) {
            do_compile = false;
            input_types.push_back(input.type());
        } else {
            input_types.push_back(ValueType::double_type());
        }
    }
    NodeTypes node_types(rank_function, input_types);
    if (!node_types.all_types_are_double()) {
        do_compile = false;
    }
    ValueType root_type = node_types.get_type(rank_function.root());
    if (root_type.is_error()) {
        LOG(error, "rank expression contains type errors: %s\n", script.c_str());
        return false;
    }
    if (root_type.is_any()) {
        LOG(warning, "rank expression could produce run-time type errors: %s\n", script.c_str());
    }
    auto compile_issues = CompiledFunction::detect_issues(rank_function);
    auto interpret_issues = InterpretedFunction::detect_issues(rank_function);
    if (do_compile && compile_issues && !interpret_issues) {
        LOG(warning, "rank expression compilation disabled: %s\n%s",
            script.c_str(), list_issues(compile_issues.list).c_str());
        do_compile = false;
    }
    const auto &issues = do_compile ? compile_issues : interpret_issues;
    if (issues) {
        LOG(error, "rank expression cannot be evaluated: %s\n%s",
            script.c_str(), list_issues(issues.list).c_str());
        return false;
    }
    // avoid costly compilation when only verifying setup
    if (env.getFeatureMotivation() != env.FeatureMotivation::VERIFY_SETUP) {
        if (do_compile) {
            bool suggest_lazy = CompiledFunction::should_use_lazy_params(rank_function);
            if (fef::indexproperties::eval::LazyExpressions::check(env.getProperties(), suggest_lazy)) {
                _compile_token = CompileCache::compile(rank_function, PassParams::LAZY);
            } else {
                _compile_token = CompileCache::compile(rank_function, PassParams::ARRAY);
            }
        } else {
            _interpreted_function.reset(new InterpretedFunction(DefaultTensorEngine::ref(), rank_function, node_types));
        }
    }
    FeatureType output_type = do_compile
                              ? FeatureType::number()
                              : FeatureType::object(root_type);
    describeOutput("out", "The result of running the contained ranking expression.", output_type);
    return true;
}

fef::Blueprint::UP
RankingExpressionBlueprint::createInstance() const
{
    return std::make_unique<RankingExpressionBlueprint>(_expression_replacer);
}

fef::FeatureExecutor &
RankingExpressionBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    if (_intrinsic_expression) {
        return _intrinsic_expression->create_executor(env, stash);
    }
    if (_interpreted_function) {
        ConstArrayRef<char> input_is_object = stash.copy_array<char>(_input_is_object);
        return stash.create<InterpretedRankingExpressionExecutor>(*_interpreted_function, input_is_object);
    }
    assert(_compile_token.get() != nullptr); // will be nullptr for VERIFY_SETUP feature motivation
    if (_compile_token->get().pass_params() == PassParams::ARRAY) {
        return stash.create<CompiledRankingExpressionExecutor>(_compile_token->get());
    } else {
        assert(_compile_token->get().pass_params() == PassParams::LAZY);
        return stash.create<LazyCompiledRankingExpressionExecutor>(_compile_token->get());
    }
}

//-----------------------------------------------------------------------------

} // features
} // search
