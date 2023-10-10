// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

struct MatchParams {
    MatchParams() : weights() {}
    std::vector<uint32_t> weights;
};

/**
 * Implements the executor for the match feature.
 */
class MatchExecutor : public fef::FeatureExecutor {
private:
    const MatchParams & _params;

public:
    MatchExecutor(const MatchParams & params);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the match executor.
 */
class MatchBlueprint : public fef::Blueprint {
private:
    MatchParams _params;
public:
    MatchBlueprint();
    ~MatchBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
