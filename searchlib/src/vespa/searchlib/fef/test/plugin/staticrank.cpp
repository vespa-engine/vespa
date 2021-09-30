// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "staticrank.h"
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/vespalib/util/stash.h>

namespace search::fef::test {

StaticRankExecutor::StaticRankExecutor(const search::attribute::IAttributeVector * attribute) :
    FeatureExecutor(),
    _attribute(attribute)
{
}

void
StaticRankExecutor::execute(uint32_t docId)
{
    search::attribute::FloatContent staticRank;
    if (_attribute != nullptr) {
        staticRank.allocate(_attribute->getMaxValueCount());
        staticRank.fill(*_attribute, docId);
    }
    outputs().set_number(0, static_cast<feature_t>(staticRank[0]));
}


StaticRankBlueprint::StaticRankBlueprint() :
    Blueprint("staticrank"),
    _attributeName()
{
}

StaticRankBlueprint::~StaticRankBlueprint() = default;

bool
StaticRankBlueprint::setup(const IIndexEnvironment & indexEnv, const StringVector & params)
{
    (void) indexEnv;
    if (params.size() != 1) {
        return false;
    }
    _attributeName = params[0];
    describeOutput("out", "static rank");
    return true;
}

FeatureExecutor &
StaticRankBlueprint::createExecutor(const IQueryEnvironment & queryEnv, vespalib::Stash &stash) const
{
    const search::attribute::IAttributeVector * av = queryEnv.getAttributeContext().getAttribute(_attributeName);
    return stash.create<StaticRankExecutor>(av);
}

}
