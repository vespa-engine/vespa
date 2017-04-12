// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

class SubqueriesExecutor : public search::fef::FeatureExecutor {
    std::vector<search::fef::TermFieldHandle> _handles;
    const fef::MatchData                     *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    SubqueriesExecutor(const search::fef::IQueryEnvironment &env,
                       uint32_t fieldId);
    virtual void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class SubqueriesBlueprint : public search::fef::Blueprint
{
private:
    const search::fef::FieldInfo *_field;
public:
    SubqueriesBlueprint() : Blueprint("subqueries"), _field(nullptr) {}
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                   search::fef::IDumpFeatureVisitor &) const override {}
    virtual search::fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new SubqueriesBlueprint);
    }
    virtual search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().desc().field();
    }
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params) override;
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

