// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

/**
 * Implements the executor for field length.
 */
class FieldLengthExecutor : public search::fef::FeatureExecutor {
private:
    std::vector<search::fef::TermFieldHandle> _fieldHandles;
    const fef::MatchData             *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    /**
     * Constructs an executor for field length.
     *
     * @param env       The query environment
     * @param fieldId   The field id
     */
    FieldLengthExecutor(const search::fef::IQueryEnvironment &env,
                        uint32_t fieldId);
    virtual void execute(search::fef::MatchData &data);
};

/**
 * Implements the blueprint for field length.
 */
class FieldLengthBlueprint : public search::fef::Blueprint {
private:
    const search::fef::FieldInfo *_field;

public:
    /**
     * Constructs a blueprint for field length.
     */
    FieldLengthBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::SINGLE);
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);
};

}}

