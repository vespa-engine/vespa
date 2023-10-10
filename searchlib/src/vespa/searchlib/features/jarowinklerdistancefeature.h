// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

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
class JaroWinklerDistanceExecutor : public fef::FeatureExecutor {
public:
    /**
     * Constructs a new executor for the jaro winkler distance calculator.
     *
     * @param config The config for this executor.
     */
    JaroWinklerDistanceExecutor(const fef::IQueryEnvironment &env,
                                const JaroWinklerDistanceConfig &config);
    void execute(uint32_t docId) override;

private:
    feature_t jaroWinklerProximity(const std::vector<fef::FieldPositionsIterator> &termPos, uint32_t fieldLen);

private:
    const JaroWinklerDistanceConfig  &_config;      // The config for this executor.
    std::vector<fef::TermFieldHandle> _termFieldHandles; // The handles of all query terms.
    const fef::MatchData             *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;
};

/**
 * Implements the blueprint for the jaro winkler distance calculator.
 */
class JaroWinklerDistanceBlueprint : public fef::Blueprint {
public:
    /**
     * Constructs a new blueprint for the jaro winkler distance calculator.
     */
    JaroWinklerDistanceBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::SINGLE);
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

private:
    JaroWinklerDistanceConfig _config; // The config for this blueprint.
};

}
