// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace fef {
namespace test {

class SumExecutor : public FeatureExecutor
{
public:
    bool isPure() override { return true; }
    void execute(uint32_t docId) override;
};


class SumBlueprint : public Blueprint
{
public:
    SumBlueprint();
     void visitDumpFeatures(const IIndexEnvironment & indexEnv, IDumpFeatureVisitor & visitor) const override;
    Blueprint::UP createInstance() const override { return Blueprint::UP(new SumBlueprint()); }
    bool setup(const IIndexEnvironment & indexEnv, const StringVector & params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment & queryEnv, vespalib::Stash &stash) const override;
};

} // namespace test
} // namespace fef
} // namespace search

