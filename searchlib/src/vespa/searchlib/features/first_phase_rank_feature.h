// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "first_phase_rank_lookup.h"
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::features {

class FirstPhaseRankLookup;

/*
 * Executor for first phase rank feature that outputs the first phase rank
 * for the given docid on this search node (1.0, 2.0, 3.0, etc.).
 */
class FirstPhaseRankExecutor : public fef::FeatureExecutor {
    const FirstPhaseRankLookup& _lookup;
public:
    FirstPhaseRankExecutor(const FirstPhaseRankLookup& lookup);
    ~FirstPhaseRankExecutor() override;
    void execute(uint32_t docid) override;
};

/*
 * Blueprint for first phase rank feature.
 */
class FirstPhaseRankBlueprint : public fef::Blueprint {
public:
    FirstPhaseRankBlueprint();
    ~FirstPhaseRankBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment& env, fef::IDumpFeatureVisitor& visitor) const override;
    std::unique_ptr<fef::Blueprint> createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment& env, const fef::ParameterList& params) override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    fef::FeatureExecutor& createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const override;
};

}
