// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/fef.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

struct DebugWaitParams {
    double waitTime;
    bool   busyWait;
};

//-----------------------------------------------------------------------------

class DebugWaitBlueprint : public fef::Blueprint
{
private:
    DebugWaitParams _params;

public:
    DebugWaitBlueprint();

    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().number().number();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor::LP createExecutor(const fef::IQueryEnvironment & env) const override;
};

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search

