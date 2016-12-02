// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

class ItemRawScoreExecutor : public fef::FeatureExecutor
{
public:
    typedef std::vector<fef::TermFieldHandle> HandleVector;
private:
    HandleVector _handles;
public:
    ItemRawScoreExecutor(HandleVector handles)
        : FeatureExecutor(), _handles(handles) {}
    virtual void execute(fef::MatchData &data);
};

class SimpleItemRawScoreExecutor : public fef::FeatureExecutor
{
private:
    fef::TermFieldHandle _handle;
public:
    SimpleItemRawScoreExecutor(fef::TermFieldHandle handle)
        : FeatureExecutor(), _handle(handle) {}
    virtual void execute(fef::MatchData &data);
};


//-----------------------------------------------------------------------------

class ItemRawScoreBlueprint : public fef::Blueprint
{
private:
    typedef std::vector<fef::TermFieldHandle> HandleVector;
    vespalib::string _label;
public:
    ItemRawScoreBlueprint() : Blueprint("itemRawScore"), _label() {}
    virtual void visitDumpFeatures(const fef::IIndexEnvironment &,
                                   fef::IDumpFeatureVisitor &) const {}
    virtual fef::Blueprint::UP createInstance() const {
        return Blueprint::UP(new ItemRawScoreBlueprint());
    }
    virtual fef::ParameterDescriptions getDescriptions() const {
        return fef::ParameterDescriptions().desc().string();
    }
    virtual bool setup(const fef::IIndexEnvironment &env,
                       const fef::ParameterList &params);
    virtual fef::FeatureExecutor::LP
    createExecutor(const fef::IQueryEnvironment &env) const override;

    static HandleVector resolve(const fef::IQueryEnvironment &env,
                                const vespalib::string &label);
};

} // namespace features
} // namespace search

