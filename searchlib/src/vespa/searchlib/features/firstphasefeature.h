// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the executor outputting the first phase ranking.
 */
class FirstPhaseExecutor : public fef::FeatureExecutor {
public:
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
};

/**
 * Implements the blueprint for the first phase feature.
 */
class FirstPhaseBlueprint : public fef::Blueprint {
public:
    FirstPhaseBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;

    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;

    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
