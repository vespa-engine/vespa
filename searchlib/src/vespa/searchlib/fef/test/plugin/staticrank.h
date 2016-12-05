// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace fef {
namespace test {

class StaticRankExecutor : public FeatureExecutor
{
private:
    const search::attribute::IAttributeVector * _attribute;

public:
    StaticRankExecutor(const search::attribute::IAttributeVector * attribute);
    virtual void execute(MatchData & data);
};


class StaticRankBlueprint : public Blueprint
{
private:
    std::string _attributeName;

public:
    StaticRankBlueprint();
    virtual void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const {}
    virtual Blueprint::UP createInstance() const { return Blueprint::UP(new StaticRankBlueprint()); }
    virtual bool setup(const IIndexEnvironment & indexEnv, const StringVector & params);
    virtual FeatureExecutor &createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

} // namespace test
} // namespace fef
} // namespace search

