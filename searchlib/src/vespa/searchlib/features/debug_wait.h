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

class DebugWaitExecutor : public search::fef::FeatureExecutor
{
private:
    DebugWaitParams _params;

public:
    DebugWaitExecutor(const search::fef::IQueryEnvironment &env,
                      const DebugWaitParams &params);
    virtual void execute(search::fef::MatchData & data);
};

//-----------------------------------------------------------------------------

class DebugWaitBlueprint : public search::fef::Blueprint
{
private:
    DebugWaitParams _params;

public:
    DebugWaitBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().number().number();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params);

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment & env) const;
};

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search

