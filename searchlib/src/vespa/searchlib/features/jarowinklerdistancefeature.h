// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/common/feature.h>

namespace search {
namespace features {

/**
 * Implements the necessary config to pass from the jaro winkler distance blueprint to the executor.
 */
struct JaroWinklerDistanceConfig {
    JaroWinklerDistanceConfig();

    uint32_t  fieldId;        // The id of field to process.
    uint32_t  fieldBegin;     // The first field term to evaluate.
    uint32_t  fieldEnd;       // The last field term to evaluate.
    feature_t boostThreshold; // The jaro threshold to exceed to apply boost.
    uint32_t  prefixSize;     // The number of characters to use for boost.
};

/**
 * Implements the executor for the jaro winkler distance calculator.
 */
class JaroWinklerDistanceExecutor : public search::fef::FeatureExecutor {
public:
    /**
     * Constructs a new executor for the jaro winkler distance calculator.
     *
     * @param config The config for this executor.
     */
    JaroWinklerDistanceExecutor(const search::fef::IQueryEnvironment &env,
                                const JaroWinklerDistanceConfig &config);
    void inputs_done() override { _lenHandle = inputs()[0]; }
    virtual void execute(search::fef::MatchData &data);

private:
    feature_t jaroWinklerProximity(const std::vector<search::fef::FieldPositionsIterator> &termPos, uint32_t fieldLen);

private:
    const JaroWinklerDistanceConfig          &_config;      // The config for this executor.
    std::vector<search::fef::TermFieldHandle> _termFieldHandles; // The handles of all query terms.
    search::fef::FeatureHandle                _lenHandle;   // Handle to the length input feature.
};

/**
 * Implements the blueprint for the jaro winkler distance calculator.
 */
class JaroWinklerDistanceBlueprint : public search::fef::Blueprint {
public:
    /**
     * Constructs a new blueprint for the jaro winkler distance calculator.
     */
    JaroWinklerDistanceBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::SINGLE);
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment &env) const;

private:
    JaroWinklerDistanceConfig _config; // The config for this blueprint.
};

}}

