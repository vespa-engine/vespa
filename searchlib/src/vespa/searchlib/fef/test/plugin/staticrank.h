// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    void execute(uint32_t docId) override;
};


class StaticRankBlueprint : public Blueprint
{
private:
    std::string _attributeName;

public:
    StaticRankBlueprint();
    ~StaticRankBlueprint();
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new StaticRankBlueprint()); }
    bool setup(const IIndexEnvironment & indexEnv, const StringVector & params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

} // namespace test
} // namespace fef
} // namespace search

