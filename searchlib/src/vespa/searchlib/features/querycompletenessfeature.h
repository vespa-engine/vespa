// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

/**
 * Implements the necessary config for query completeness.
 */
struct QueryCompletenessConfig {
    QueryCompletenessConfig();

    uint32_t fieldId;    // The id of field to process.
    uint32_t fieldBegin; // The first field token to evaluate.
    uint32_t fieldEnd;   // The last field token to evaluate.
};

/**
 * Implements the executor for query completeness.
 */
class QueryCompletenessExecutor : public fef::FeatureExecutor {
public:
    /**
     * Constructs an executor for query completenes.
     *
     * @param env    The query environment.
     * @param config The completeness config.
     */
    QueryCompletenessExecutor(const fef::IQueryEnvironment &env,
                              const QueryCompletenessConfig &config);
    void execute(uint32_t docId) override;

private:
    const QueryCompletenessConfig            &_config;
    std::vector<fef::TermFieldHandle> _fieldHandles;
    const fef::MatchData                     *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;
};

/**
 * Implements the blueprint for query completeness.
 */
class QueryCompletenessBlueprint : public fef::Blueprint {
public:
    QueryCompletenessBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment &env, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().
            desc().indexField(fef::ParameterCollection::ANY).
            desc().indexField(fef::ParameterCollection::ANY).number().
            desc().indexField(fef::ParameterCollection::ANY).number().number();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

private:
    QueryCompletenessConfig _config;
};

}
