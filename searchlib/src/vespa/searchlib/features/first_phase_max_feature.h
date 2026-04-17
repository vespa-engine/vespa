// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "first_phase_max.h"

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::features {

/**
 * Executor for first phase max feature which outputs the max score from first
 * phase.
 */
class FirstPhaseMaxExecutor : public fef::FeatureExecutor {
    const FirstPhaseMax& _max;

public:
    explicit FirstPhaseMaxExecutor(const FirstPhaseMax& max);
    ~FirstPhaseMaxExecutor() override;

    void execute(uint32_t) override;
};

/**
 * Blueprint for first phase max feature.
 */
class FirstPhaseMaxBlueprint : public fef::Blueprint {
public:
    FirstPhaseMaxBlueprint();
    ~FirstPhaseMaxBlueprint() override;

    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
    fef::ParameterDescriptions getDescriptions() const override;
    std::unique_ptr<fef::Blueprint> createInstance() const override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
};

}

