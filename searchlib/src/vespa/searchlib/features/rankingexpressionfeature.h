// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/eval/compiled_function.h>
#include <vespa/vespalib/eval/interpreted_function.h>
#include <vespa/vespalib/eval/compile_cache.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

/**
 * Implements the executor for compiled ranking expressions
 **/
class CompiledRankingExpressionExecutor : public search::fef::FeatureExecutor
{
private:
    typedef double (*arr_function)(const double *);
    arr_function _ranking_function;
    std::vector<double> _params;

public:
    CompiledRankingExpressionExecutor(const vespalib::eval::CompiledFunction &compiled_function);
    virtual void execute(search::fef::MatchData &data);
};

//-----------------------------------------------------------------------------

/**
 * Implements the executor for interpreted ranking expressions (with tensor support)
 **/
class InterpretedRankingExpressionExecutor : public search::fef::FeatureExecutor
{
private:
    vespalib::eval::InterpretedFunction::Context _context;
    const vespalib::eval::InterpretedFunction   &_function;

public:
    InterpretedRankingExpressionExecutor(const vespalib::eval::InterpretedFunction &function);
    virtual void execute(search::fef::MatchData &data);
};

//-----------------------------------------------------------------------------

/**
 * Implements the blueprint for ranking expression.
 */
class RankingExpressionBlueprint : public search::fef::Blueprint
{
private:
    vespalib::eval::InterpretedFunction::UP _interpreted_function;
    vespalib::eval::CompileCache::Token::UP _compile_token;

public:
    /**
     * Constructs a ranking expression blueprint.
     */
    RankingExpressionBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().
            desc().
            desc().string();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment &env) const;
};

//-----------------------------------------------------------------------------

} // features
} // search
