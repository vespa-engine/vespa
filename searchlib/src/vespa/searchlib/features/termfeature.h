// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>

namespace search {
namespace features {

/**
 * Implements the executor for term feature.
 */
class TermExecutor : public search::fef::FeatureExecutor {
public:
    /**
     * Constructs an executor for term feature.
     *
     * @param env    The query environment.
     * @param termId The id of the query term to evaluate.
     */
    TermExecutor(const search::fef::IQueryEnvironment &env,
                 uint32_t termId);
    virtual void execute(search::fef::MatchData &data);

private:
    const search::fef::ITermData *_termData;
    feature_t                     _connectedness;
    feature_t                     _significance;
};

/**
 * Implements the blueprint for term feature.
 */
class TermBlueprint : public search::fef::Blueprint {
public:
    /**
     * Constructs a blueprint for term feature.
     */
    TermBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().number();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment &env) const;

private:
    uint32_t _termId;
};

}}

