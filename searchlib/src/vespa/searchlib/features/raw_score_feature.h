// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

class RawScoreExecutor : public search::fef::FeatureExecutor
{
private:
    std::vector<search::fef::TermFieldHandle> _handles;
    const fef::MatchData                     *_md;

    virtual void handle_bind_match_data(fef::MatchData &md) override;
public:
    RawScoreExecutor(const search::fef::IQueryEnvironment &env, uint32_t fieldId);
    virtual void execute(search::fef::MatchData &data);
};

//-----------------------------------------------------------------------------

class RawScoreBlueprint : public search::fef::Blueprint
{
private:
    const search::fef::FieldInfo *_field;
public:
    RawScoreBlueprint() : Blueprint("rawScore"), _field(0) {}
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                   search::fef::IDumpFeatureVisitor &) const {}
    virtual search::fef::Blueprint::UP createInstance() const {
        return Blueprint::UP(new RawScoreBlueprint());
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

