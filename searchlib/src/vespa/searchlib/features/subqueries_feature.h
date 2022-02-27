// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

class SubqueriesExecutor : public fef::FeatureExecutor {
    std::vector<fef::TermFieldHandle> _handles;
    const fef::MatchData             *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;
public:
    SubqueriesExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId);
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class SubqueriesBlueprint : public fef::Blueprint
{
private:
    const fef::FieldInfo *_field;
public:
    SubqueriesBlueprint() : Blueprint("subqueries"), _field(nullptr) {}
    ~SubqueriesBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment &, fef::IDumpFeatureVisitor &) const override {}
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new SubqueriesBlueprint);
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
