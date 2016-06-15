// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.staticrank");
#include <vespa/searchcommon/attribute/attributecontent.h>
#include "staticrank.h"

namespace search {
namespace fef {
namespace test {

StaticRankExecutor::StaticRankExecutor(const search::attribute::IAttributeVector * attribute) :
    FeatureExecutor(),
    _attribute(attribute)
{
}

void
StaticRankExecutor::execute(MatchData & data)
{
    uint32_t doc = data.getDocId();
    search::attribute::FloatContent staticRank;
    if (_attribute != NULL) {
        staticRank.allocate(_attribute->getMaxValueCount());
        staticRank.fill(*_attribute, doc);
    }
    *data.resolveFeature(outputs()[0]) = static_cast<feature_t>(staticRank[0]);
}


StaticRankBlueprint::StaticRankBlueprint() :
    Blueprint("staticrank"),
    _attributeName()
{
}

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

FeatureExecutor::LP
StaticRankBlueprint::createExecutor(const IQueryEnvironment & queryEnv) const
{
    const search::attribute::IAttributeVector * av = queryEnv.getAttributeContext().getAttribute(_attributeName);
    return FeatureExecutor::LP(new StaticRankExecutor(av));
}

} // namespace test
} // namespace fef
} // namespace search
