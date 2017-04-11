// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/table.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>

namespace search {
namespace features {

/**
 * Implements the executor for looking at term field match data
 **/
class TermFieldMdExecutor : public fef::FeatureExecutor {

    typedef std::pair<search::fef::TermFieldHandle, query::Weight> Element;
    std::vector<Element> _terms;
    const fef::MatchData *_md;

    virtual void execute(uint32_t docId) override;
    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    TermFieldMdExecutor(const search::fef::IQueryEnvironment &env,
                        uint32_t fieldId);
};


/**
 * Implements the blueprint for the term field md executor.
 **/
class TermFieldMdBlueprint : public fef::Blueprint {
    const search::fef::FieldInfo * _field;
public:
    TermFieldMdBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const fef::IIndexEnvironment & env,
                                   fef::IDumpFeatureVisitor & visitor) const override;

    // Inherit doc from Blueprint.
    virtual fef::Blueprint::UP createInstance() const override;

    // Inherit doc from Blueprint.
    virtual fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const fef::IIndexEnvironment & env,
                       const fef::ParameterList & params) override;

    // Inherit doc from Blueprint.
    virtual fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment & env, vespalib::Stash &stash) const override;
};


} // namespace features
} // namespace search

