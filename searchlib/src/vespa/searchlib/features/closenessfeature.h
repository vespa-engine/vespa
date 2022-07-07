// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "logarithmcalculator.h"
#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the executor for the closeness feature.
 */
class ClosenessExecutor : public fef::FeatureExecutor {
private:
    feature_t _maxDistance;
    LogarithmCalculator _logCalc;

public:
    ClosenessExecutor(feature_t maxDistance, feature_t scaleDistance);
    void execute(uint32_t docId) override;
};


/**
 * Implements the blueprint for the closeness executor.
 */
class ClosenessBlueprint : public fef::Blueprint {
private:
    feature_t _maxDistance;
    feature_t _scaleDistance;
    feature_t _halfResponse;
    vespalib::string _arg_string;
    uint32_t _attr_id;
    bool _use_geo_pos;
    bool _use_nns_tensor;
    bool _use_item_label;

public:

    ClosenessBlueprint();
    ~ClosenessBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string().desc().string().string();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    void prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
