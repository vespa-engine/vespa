// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
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
    virtual void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const {};
    virtual Blueprint::UP createInstance() const { return Blueprint::UP(new QueryBlueprint()); }
    virtual bool setup(const IIndexEnvironment &indexEnv, const StringVector &params);
    virtual FeatureExecutor::LP createExecutor(const IQueryEnvironment &queryEnv) const;
};

} // namespace test
} // namespace fef
} // namespace search

