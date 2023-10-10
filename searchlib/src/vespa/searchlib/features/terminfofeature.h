// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>

namespace search::features {

class TermInfoBlueprint : public fef::Blueprint
{
private:
    uint32_t _termIdx;

public:
    TermInfoBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment &indexEnv, fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override { return fef::Blueprint::UP(new TermInfoBlueprint()); }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().number();
    }
    bool setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &queryEnv, vespalib::Stash &stash) const override;
};

}
