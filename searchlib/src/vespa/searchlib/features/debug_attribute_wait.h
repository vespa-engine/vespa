// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/fef/fef.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

struct DebugAttributeWaitParams {
    bool   busyWait;
};

//-----------------------------------------------------------------------------

class DebugAttributeWaitBlueprint : public fef::Blueprint
{
private:
    vespalib::string _attribute;
    DebugAttributeWaitParams _params;

public:
    DebugAttributeWaitBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment & env, fef::IDumpFeatureVisitor & visitor) const override;
    fef::Blueprint::UP createInstance() const override;
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().attribute(fef::ParameterCollection::ANY).number();
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search

