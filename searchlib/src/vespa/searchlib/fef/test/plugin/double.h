// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace fef {
namespace test {

class DoubleExecutor : public FeatureExecutor
{
private:
    size_t _cnt;
public:
    DoubleExecutor(size_t cnt) : _cnt(cnt) {}
    void execute(uint32_t docId) override;
};


class DoubleBlueprint : public Blueprint
{
private:
    size_t _cnt;
public:
    DoubleBlueprint();
    void visitDumpFeatures(const IIndexEnvironment & indexEnv, IDumpFeatureVisitor & visitor) const override;
    Blueprint::UP createInstance() const override { return Blueprint::UP(new DoubleBlueprint()); }
    bool setup(const IIndexEnvironment & indexEnv, const StringVector & params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

} // namespace test
} // namespace fef
} // namespace search

