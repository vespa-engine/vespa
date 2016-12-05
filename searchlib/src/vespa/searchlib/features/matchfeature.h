// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

struct MatchParams {
    MatchParams() : weights() {}
    std::vector<uint32_t> weights;
};

/**
 * Implements the executor for the match feature.
 */
class MatchExecutor : public search::fef::FeatureExecutor {
private:
    const MatchParams & _params;

public:
    /**
     * Constructs an executor.
     */
    MatchExecutor(const MatchParams & params);
    virtual void execute(search::fef::MatchData & data);
};


/**
 * Implements the blueprint for the match executor.
 */
class MatchBlueprint : public search::fef::Blueprint {
private:
    MatchParams _params;

public:
    /**
     * Constructs a blueprint.
     */
    MatchBlueprint();

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
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

