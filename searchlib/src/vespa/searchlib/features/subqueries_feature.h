// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

class SubqueriesExecutor : public search::fef::FeatureExecutor {
    std::vector<search::fef::TermFieldHandle> _handles;
public:
    SubqueriesExecutor(const search::fef::IQueryEnvironment &env,
                       uint32_t fieldId);
    virtual void execute(search::fef::MatchData &data);
};

//-----------------------------------------------------------------------------

class SubqueriesBlueprint : public search::fef::Blueprint
{
private:
    const search::fef::FieldInfo *_field;
public:
    SubqueriesBlueprint() : Blueprint("subqueries"), _field(nullptr) {}
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                   search::fef::IDumpFeatureVisitor &) const {}
    virtual search::fef::Blueprint::UP createInstance() const {
        return Blueprint::UP(new SubqueriesBlueprint);
    }
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().field();
    }
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params);
    virtual search::fef::FeatureExecutor::LP
    createExecutor(const search::fef::IQueryEnvironment &env) const;
};

} // namespace features
} // namespace search

