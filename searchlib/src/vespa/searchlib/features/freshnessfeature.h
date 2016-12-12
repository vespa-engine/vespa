// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include "logarithmcalculator.h"

namespace search {
namespace features {

/**
 * Implements the executor for the freshness feature.
 */
class FreshnessExecutor : public search::fef::FeatureExecutor {
private:
    feature_t _maxAge;
    LogarithmCalculator _logCalc;

public:
    /**
     * Constructs an executor.
     */
    FreshnessExecutor(feature_t maxAge, feature_t scaleAge);
    virtual void execute(uint32_t docId);
};


/**
 * Implements the blueprint for the freshness executor.
 */
class FreshnessBlueprint : public search::fef::Blueprint {
private:
    feature_t _maxAge;
    feature_t _halfResponse;
    feature_t _scaleAge;

public:
    /**
     * Constructs a blueprint.
     */
    FreshnessBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().attribute(search::fef::ParameterCollection::ANY);
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

