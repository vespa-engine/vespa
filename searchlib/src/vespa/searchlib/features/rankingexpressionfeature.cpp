// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".features.rankingexpression");

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/features/rankingexpression/feature_name_extractor.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/eval/function.h>
#include <vespa/vespalib/eval/compiled_function.h>
#include <vespa/vespalib/eval/compile_cache.h>
#include <vespa/vespalib/eval/node_types.h>
#include "rankingexpressionfeature.h"
#include "utils.h"
#include <stdexcept>
#include <vespa/vespalib/eval/value_type.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/vespalib/tensor/default_tensor_engine.h>

using vespalib::eval::Function;
using vespalib::eval::PassParams;
using vespalib::eval::CompileCache;
using vespalib::eval::CompiledFunction;
using vespalib::eval::InterpretedFunction;
using vespalib::eval::ValueType;
using vespalib::eval::NodeTypes;
using vespalib::tensor::DefaultTensorEngine;
using search::fef::FeatureType;

namespace search {
namespace features {

//-----------------------------------------------------------------------------

CompiledRankingExpressionExecutor::CompiledRankingExpressionExecutor(const vespalib::eval::CompiledFunction &compiled_function)
    : _ranking_function(compiled_function.get_function()),
      _params(compiled_function.num_params(), 0.0)
{
}

void
CompiledRankingExpressionExecutor::execute(search::fef::MatchData &data)
{
    for (size_t i = 0; i < _params.size(); ++i) {
        _params[i] = *data.resolveFeature(inputs()[i]);
    }
    *data.resolveFeature(outputs()[0]) = _ranking_function(&_params[0]);
}

//-----------------------------------------------------------------------------

InterpretedRankingExpressionExecutor::InterpretedRankingExpressionExecutor(const vespalib::eval::InterpretedFunction &function)
    : _context(),
      _function(function)
{
}

void
InterpretedRankingExpressionExecutor::execute(search::fef::MatchData &data)
{
    _context.clear_params();
    for (size_t i = 0; i < _function.num_params(); ++i) {
        if (data.feature_is_object(inputs()[i])) {
            _context.add_param(*data.resolve_object_feature(inputs()[i]));
        } else {
            _context.add_param(*data.resolveFeature(inputs()[i]));
        }
    }
    *data.resolve_object_feature(outputs()[0]) = _function.eval(_context);
}

//-----------------------------------------------------------------------------

RankingExpressionBlueprint::RankingExpressionBlueprint()
    : search::fef::Blueprint("rankingExpression"),
      _interpreted_function(),
      _compile_token()
{
}

void
RankingExpressionBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                              search::fef::IDumpFeatureVisitor &) const
{
    // empty
}

bool
RankingExpressionBlueprint::setup(const search::fef::IIndexEnvironment &env,
                                  const search::fef::ParameterList &params)
{
    // Retrieve and concatenate whatever config is available.
    vespalib::string script = "";
    search::fef::Property property = env.getProperties().lookup(getName(), "rankingScript");
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
    bool do_compile = true;
    std::vector<ValueType> input_types;
    for (size_t i = 0; i < rank_function.num_params(); ++i) {
        const FeatureType &input = defineInput(rank_function.param_name(i), AcceptInput::ANY);
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
    // avoid costly compilation when only verifying setup
    if (env.getFeatureMotivation() != env.FeatureMotivation::VERIFY_SETUP) {
        if (do_compile) {
            _compile_token = CompileCache::compile(rank_function, PassParams::ARRAY);
        } else {
            _interpreted_function.reset(new InterpretedFunction(DefaultTensorEngine::ref(), rank_function));
        }
    }
    FeatureType output_type = do_compile
                              ? FeatureType::number()
                              : FeatureType::object(root_type);
    describeOutput("out", "The result of running the contained ranking expression.", output_type);
    return true;
}

search::fef::Blueprint::UP
RankingExpressionBlueprint::createInstance() const
{
    return search::fef::Blueprint::UP(new RankingExpressionBlueprint());
}

search::fef::FeatureExecutor::LP
RankingExpressionBlueprint::createExecutor(const search::fef::IQueryEnvironment &) const
{
    if (_interpreted_function) {
        return search::fef::FeatureExecutor::LP(new InterpretedRankingExpressionExecutor(*_interpreted_function));
    }
    assert(_compile_token.get() != nullptr); // will be nullptr for VERIFY_SETUP feature motivation
    return search::fef::FeatureExecutor::LP(new CompiledRankingExpressionExecutor(_compile_token->get()));
}

//-----------------------------------------------------------------------------

} // features
} // search
