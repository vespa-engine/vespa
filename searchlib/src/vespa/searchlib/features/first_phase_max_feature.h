// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::features {

/**
 * Executor for first phase max feature which outputs the max score from first
 * phase.
 */
class FirstPhaseMaxExecutor : public fef::FeatureExecutor {
    const feature_t& _max;

public:
    explicit FirstPhaseMaxExecutor(const feature_t& max);
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

    // for tests
    static void make_shared_state(fef::IObjectStore& store);
    static const feature_t* get_shared_state(const fef::IObjectStore& store);
    static feature_t* get_mutable_shared_state(fef::IObjectStore& store);

    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
    fef::ParameterDescriptions getDescriptions() const override;
    std::unique_ptr<fef::Blueprint> createInstance() const override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
};

}

