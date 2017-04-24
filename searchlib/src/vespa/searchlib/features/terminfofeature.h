// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

class TermInfoBlueprint : public search::fef::Blueprint
{
private:
    uint32_t _termIdx;

public:
    TermInfoBlueprint();
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &indexEnv,
                                   search::fef::IDumpFeatureVisitor &visitor) const override;
    virtual search::fef::Blueprint::UP createInstance() const override { return search::fef::Blueprint::UP(new TermInfoBlueprint()); }
    virtual search::fef::ParameterDescriptions getDescriptions() const override {
        return search::fef::ParameterDescriptions().desc().number();
    }
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params) override;
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

} // namespace features
} // namespace search

