// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/eval/eval/fast_forest.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/searchlib/features/rankingexpression/expression_replacer.h>
#include <vespa/searchlib/features/rankingexpression/intrinsic_expression.h>

namespace search::features {

//-----------------------------------------------------------------------------

/**
 * Implements the blueprint for ranking expression.
 */
class RankingExpressionBlueprint : public fef::Blueprint
{
private:
    rankingexpression::ExpressionReplacer::SP  _expression_replacer;
    rankingexpression::IntrinsicExpression::UP _intrinsic_expression;
    vespalib::eval::gbdt::FastForest::UP       _fast_forest;
    vespalib::eval::InterpretedFunction::UP    _interpreted_function;
    vespalib::eval::CompileCache::Token::UP    _compile_token;
    std::vector<char>                          _input_is_object;
    bool                                       _should_unbox;

public:
    RankingExpressionBlueprint();
    RankingExpressionBlueprint(rankingexpression::ExpressionReplacer::SP replacer);
    ~RankingExpressionBlueprint() override;

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().
            desc().
            desc().string();
    }

    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    void prepareSharedState(const fef::IQueryEnvironment & queryEnv, fef::IObjectStore & objectStore) const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
