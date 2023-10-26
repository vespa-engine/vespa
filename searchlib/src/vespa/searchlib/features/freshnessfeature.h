// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "logarithmcalculator.h"
#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the executor for the freshness feature.
 */
class FreshnessExecutor : public fef::FeatureExecutor {
private:
    feature_t _maxAge;
    LogarithmCalculator _logCalc;

public:
    FreshnessExecutor(feature_t maxAge, feature_t scaleAge);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the freshness executor.
 */
class FreshnessBlueprint : public fef::Blueprint {
private:
    feature_t _maxAge;
    feature_t _halfResponse;
    feature_t _scaleAge;

public:
    FreshnessBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override;
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
