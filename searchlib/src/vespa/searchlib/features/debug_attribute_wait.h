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

class DebugAttributeWaitExecutor : public search::fef::FeatureExecutor
{
private:
    const search::attribute::IAttributeVector *_attribute;
    search::attribute::FloatContent _buf;
    DebugAttributeWaitParams _params;

public:
    DebugAttributeWaitExecutor(const search::fef::IQueryEnvironment &env,
                               const search::attribute::IAttributeVector *
                               attribute,
                      const DebugAttributeWaitParams &params);
    virtual void execute(search::fef::MatchData & data);
};

//-----------------------------------------------------------------------------

class DebugAttributeWaitBlueprint : public search::fef::Blueprint
{
private:
    vespalib::string _attribute;
    DebugAttributeWaitParams _params;

public:
    DebugAttributeWaitBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().attribute(search::fef::ParameterCollection::ANY).number();
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

