// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

/**
 * Implements the necessary config for reverse proximity.
 */
struct ReverseProximityConfig {
    ReverseProximityConfig();

    uint32_t fieldId;    // The id of field to process.
    uint32_t termA;      // The id of the first query term in the pair (a, b).
    uint32_t termB;      // The id of the second query term.
};

/**
 * Implements the executor for reverse proximity.
 */
class ReverseProximityExecutor : public search::fef::FeatureExecutor {
public:
    /**
     * Constructs an executor for reverse proximity.
     *
     * @param env    The query environment.
     * @param config The completeness config.
     */
    ReverseProximityExecutor(const search::fef::IQueryEnvironment &env,
                             const ReverseProximityConfig &config);
    virtual void execute(uint32_t docId) override;

private:
    const ReverseProximityConfig &_config; // The proximity config.
    search::fef::TermFieldHandle  _termA;  // Handle to the first query term.
    search::fef::TermFieldHandle  _termB;  // Handle to the second query term.
    const fef::MatchData         *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;
};

/**
 * Implements the blueprint for proximity.
 */
class ReverseProximityBlueprint : public search::fef::Blueprint {
public:
    /**
     * Constructs a blueprint for reverse proximity.
     */
    ReverseProximityBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const override;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const override;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::ANY).number().number();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params) override;

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

private:
    ReverseProximityConfig _config;
};

}}

