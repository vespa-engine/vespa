// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/stllike/smallvector.h>

namespace search {
namespace features {

class ItemRawScoreExecutor : public search::fef::FeatureExecutor
{
public:
    typedef std::vector<search::fef::TermFieldHandle> HandleVector;
private:
    HandleVector _handles;
public:
    ItemRawScoreExecutor(HandleVector handles)
        : FeatureExecutor(), _handles(handles) {}
    virtual void execute(search::fef::MatchData &data);
};

class SimpleItemRawScoreExecutor : public search::fef::FeatureExecutor
{
private:
    search::fef::TermFieldHandle _handle;
public:
    SimpleItemRawScoreExecutor(search::fef::TermFieldHandle handle)
        : FeatureExecutor(), _handle(handle) {}
    virtual void execute(search::fef::MatchData &data);
};


//-----------------------------------------------------------------------------

class ItemRawScoreBlueprint : public search::fef::Blueprint
{
private:
    typedef std::vector<search::fef::TermFieldHandle> HandleVector;
    vespalib::string _label;
public:
    ItemRawScoreBlueprint() : Blueprint("itemRawScore"), _label() {}
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                   search::fef::IDumpFeatureVisitor &) const {}
    virtual search::fef::Blueprint::UP createInstance() const {
        return Blueprint::UP(new ItemRawScoreBlueprint());
    }
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().string();
    }
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params);
    virtual search::fef::FeatureExecutor::LP
    createExecutor(const search::fef::IQueryEnvironment &env) const;

    static HandleVector resolve(const search::fef::IQueryEnvironment &env,
                                const vespalib::string &label);
};

} // namespace features
} // namespace search

