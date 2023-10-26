// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace fef {
namespace test {

class QueryBlueprint : public Blueprint
{
private:
    std::string _key;

public:
    QueryBlueprint();
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override {}
    Blueprint::UP createInstance() const override { return Blueprint::UP(new QueryBlueprint()); }
    bool setup(const IIndexEnvironment &indexEnv, const StringVector &params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

} // namespace test
} // namespace fef
} // namespace search

