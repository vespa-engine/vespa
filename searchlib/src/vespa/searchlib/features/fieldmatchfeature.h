// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/features/fieldmatch/computer.h>
#include <vespa/searchlib/features/fieldmatch/params.h>
#include <vespa/searchlib/fef/blueprint.h>

namespace search {
namespace features {

/**
 * Implements the executor for THE field match feature.
 */
class FieldMatchExecutor : public search::fef::FeatureExecutor {
private:
    search::fef::PhraseSplitter             _splitter;
    const search::fef::FieldInfo          & _field;
    const fieldmatch::Params              & _params;
    fieldmatch::Computer                    _cmp;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    /**
     * Constructs an executor.
     */
    FieldMatchExecutor(const search::fef::IQueryEnvironment & queryEnv,
                       const search::fef::FieldInfo & field,
                       const fieldmatch::Params & params);
    virtual void execute(search::fef::MatchData & data);
};


/**
 * Implements the blueprint for THE field match feature.
 */
class FieldMatchBlueprint : public search::fef::Blueprint {
private:
    const search::fef::FieldInfo * _field;
    fieldmatch::Params _params;

public:
    /**
     * Constructs a blueprint.
     */
    FieldMatchBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

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
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

