// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search {
namespace features {

/**
 * Implements the executor outputting the first phase ranking.
 */
class FirstPhaseExecutor : public search::fef::FeatureExecutor {
public:
    virtual bool isPure() { return true; }
    virtual void execute(search::fef::MatchData & data);
};


/**
 * Implements the blueprint for the first phase feature.
 */
class FirstPhaseBlueprint : public search::fef::Blueprint {
public:
    /**
     * Constructs a blueprint.
     */
    FirstPhaseBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment & env) const override;
};


} // namespace features
} // namespace search

