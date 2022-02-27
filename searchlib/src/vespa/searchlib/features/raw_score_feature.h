// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

class RawScoreExecutor : public fef::FeatureExecutor
{
private:
    std::vector<fef::TermFieldHandle> _handles;
    const fef::MatchData             *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;
public:
    RawScoreExecutor(const fef::IQueryEnvironment &env, uint32_t fieldId);
    void execute(uint32_t docId) override;
};

//-----------------------------------------------------------------------------

class RawScoreBlueprint : public fef::Blueprint
{
private:
    const fef::FieldInfo *_field;
public:
    RawScoreBlueprint() : Blueprint("rawScore"), _field(0) {}
    ~RawScoreBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment &,
                           fef::IDumpFeatureVisitor &) const override {}
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new RawScoreBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().field();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
