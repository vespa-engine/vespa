// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

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
class QueryCompletenessExecutor : public search::fef::FeatureExecutor {
public:
    /**
     * Constructs an executor for query completenes.
     *
     * @param env    The query environment.
     * @param config The completeness config.
     */
    QueryCompletenessExecutor(const search::fef::IQueryEnvironment &env,
                              const QueryCompletenessConfig &config);
    virtual void execute(search::fef::MatchData &data);

private:
    const QueryCompletenessConfig            &_config;
    std::vector<search::fef::TermFieldHandle> _fieldHandles;
};

/**
 * Implements the blueprint for query completeness.
 */
class QueryCompletenessBlueprint : public search::fef::Blueprint {
public:
    /**
     * Constructs a completeness blueprint.
     */
    QueryCompletenessBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().
            desc().indexField(search::fef::ParameterCollection::ANY).
            desc().indexField(search::fef::ParameterCollection::ANY).number().
            desc().indexField(search::fef::ParameterCollection::ANY).number().number();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

private:
    QueryCompletenessConfig _config;
};

}}

