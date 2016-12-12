// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include "logarithmcalculator.h"

namespace search {
namespace features {

/**
 * Implements the executor for the closeness feature.
 */
class ClosenessExecutor : public search::fef::FeatureExecutor {
private:
    feature_t _maxDistance;
    LogarithmCalculator _logCalc;

public:
    /**
     * Constructs an executor.
     */
    ClosenessExecutor(feature_t maxDistance, feature_t scaleDistance);
    virtual void execute(uint32_t docId);
};


/**
 * Implements the blueprint for the closeness executor.
 */
class ClosenessBlueprint : public search::fef::Blueprint {
private:
    feature_t _maxDistance;
    feature_t _scaleDistance;
    feature_t _halfResponse;

public:
    /**
     * Constructs a blueprint.
     */
    ClosenessBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().string();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

