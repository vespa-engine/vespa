// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

/**
 * Implements the necessary config for proximity.
 */
struct ProximityConfig {
    ProximityConfig();

    uint32_t fieldId;    // The id of field to process.
    uint32_t termA;      // The id of the first query term in the pair (a, b).
    uint32_t termB;      // The id of the second query term.
};

/**
 * Implements the executor for proximity.
 */
class ProximityExecutor : public search::fef::FeatureExecutor {
public:
    /**
     * Constructs an executor for proximity.
     *
     * @param env    The query environment.
     * @param config The completeness config.
     */
    ProximityExecutor(const search::fef::IQueryEnvironment &env,
                      const ProximityConfig &config);
    virtual void execute(search::fef::MatchData &data);

private:
    const ProximityConfig       &_config; // The proximity config.
    search::fef::TermFieldHandle _termA;  // Handle to the first query term.
    search::fef::TermFieldHandle _termB;  // Handle to the second query term.
    const fef::MatchData        *_md;

    bool findBest(const fef::TermFieldMatchData &matchA,
                  const fef::TermFieldMatchData &matchB);
    virtual void handle_bind_match_data(fef::MatchData &md) override;
};

/**
 * Implements the blueprint for proximity.
 */
class ProximityBlueprint : public search::fef::Blueprint {
public:
    /**
     * Constructs a proximity blueprint.
     */
    ProximityBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::ANY).number().number();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

private:
    ProximityConfig _config;
};

}}

