// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

class ItemRawScoreExecutor : public fef::FeatureExecutor
{
public:
    typedef std::vector<fef::TermFieldHandle> HandleVector;
private:
    HandleVector _handles;
    const fef::MatchData *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    ItemRawScoreExecutor(HandleVector handles)
        : FeatureExecutor(), _handles(handles), _md(nullptr) {}
    void execute(uint32_t docId) override;
};

class SimpleItemRawScoreExecutor : public fef::FeatureExecutor
{
private:
    fef::TermFieldHandle _handle;
    const fef::MatchData *_md;

    void handle_bind_match_data(const fef::MatchData &md) override;

public:
    SimpleItemRawScoreExecutor(fef::TermFieldHandle handle)
        : FeatureExecutor(), _handle(handle), _md(nullptr) {}
    void execute(uint32_t docId) override;
};


//-----------------------------------------------------------------------------

class ItemRawScoreBlueprint : public fef::Blueprint
{
private:
    typedef std::vector<fef::TermFieldHandle> HandleVector;
    vespalib::string _label;
public:
    ItemRawScoreBlueprint() : Blueprint("itemRawScore"), _label() {}
    ~ItemRawScoreBlueprint() override;
    void visitDumpFeatures(const fef::IIndexEnvironment &, fef::IDumpFeatureVisitor &) const override {}
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new ItemRawScoreBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().string();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    static HandleVector resolve(const fef::IQueryEnvironment &env, const vespalib::string &label);
};

}
