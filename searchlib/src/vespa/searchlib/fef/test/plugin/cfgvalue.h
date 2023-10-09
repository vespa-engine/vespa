// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace fef {
namespace test {

class CfgValueBlueprint : public Blueprint
{
private:
    std::vector<feature_t> _values;

public:
    CfgValueBlueprint();
    ~CfgValueBlueprint();
    void visitDumpFeatures(const IIndexEnvironment & indexEnv, IDumpFeatureVisitor & visitor) const override;
    Blueprint::UP createInstance() const override { return Blueprint::UP(new CfgValueBlueprint()); }
    bool setup(const IIndexEnvironment & indexEnv, const StringVector & params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment & queryEnv, vespalib::Stash &stash) const override;
};

} // namespace test
} // namespace fef
} // namespace search
