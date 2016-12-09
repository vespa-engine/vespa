// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/query/weight.h>
#include <algorithm>

namespace search {
namespace features {

class NativeDotProductExecutor : public search::fef::FeatureExecutor
{
private:
    typedef std::pair<search::fef::TermFieldHandle,query::Weight> Pair;
    std::vector<Pair> _pairs;
    const fef::MatchData *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;

public:
    NativeDotProductExecutor(const search::fef::IQueryEnvironment &env, uint32_t fieldId);
    virtual void execute(search::fef::MatchData &data);
};

//-----------------------------------------------------------------------------

class NativeDotProductBlueprint : public search::fef::Blueprint
{
private:
    const search::fef::FieldInfo *_field;
public:
    NativeDotProductBlueprint() : Blueprint("nativeDotProduct"), _field(0) {}
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                   search::fef::IDumpFeatureVisitor &) const {}
    virtual search::fef::Blueprint::UP createInstance() const {
        return Blueprint::UP(new NativeDotProductBlueprint());
    }
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().field();
    }
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params);
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

