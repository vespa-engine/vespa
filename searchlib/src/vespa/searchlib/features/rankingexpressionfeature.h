// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/eval/interpreted_function.h>
#include <vespa/vespalib/eval/llvm/compile_cache.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

/**
 * Implements the blueprint for ranking expression.
 */
class RankingExpressionBlueprint : public fef::Blueprint
{
private:
    vespalib::eval::InterpretedFunction::UP _interpreted_function;
    vespalib::eval::CompileCache::Token::UP _compile_token;

public:
    /**
     * Constructs a ranking expression blueprint.
     */
    RankingExpressionBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().
            desc().
            desc().string();
    }

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor::LP createExecutor(const fef::IQueryEnvironment &env) const override;
};

//-----------------------------------------------------------------------------

} // features
} // search
