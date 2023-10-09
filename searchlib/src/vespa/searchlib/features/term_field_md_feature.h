// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/table.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/itermfielddata.h>

namespace search::features {

/**
 * Implements the executor for looking at term field match data
 **/
class TermFieldMdExecutor : public fef::FeatureExecutor {

    using Element = std::pair<fef::TermFieldHandle, query::Weight>;
    std::vector<Element> _terms;
    const fef::MatchData *_md;

    void execute(uint32_t docId) override;
    void handle_bind_match_data(const fef::MatchData &md) override;
public:
    TermFieldMdExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId);
};


/**
 * Implements the blueprint for the term field md executor.
 **/
class TermFieldMdBlueprint : public fef::Blueprint {
    const fef::FieldInfo * _field;
public:
    TermFieldMdBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment & env, vespalib::Stash &stash) const override;
};

}
