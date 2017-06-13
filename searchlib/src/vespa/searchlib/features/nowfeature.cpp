// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nowfeature.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/queryproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/fastos/time.h>

namespace search {
namespace features {

NowExecutor::NowExecutor(int64_t timestamp) :
    search::fef::FeatureExecutor(),
    _timestamp(timestamp)
{
}

void
NowExecutor::execute(uint32_t) {
    outputs().set_number(0, _timestamp);
}

void
NowBlueprint::visitDumpFeatures(const search::fef::IIndexEnvironment &,
                                search::fef::IDumpFeatureVisitor &visitor) const
{
    visitor.visitDumpFeature(getBaseName());
}

bool
NowBlueprint::setup(const search::fef::IIndexEnvironment &,
                    const search::fef::ParameterList &)
{
    describeOutput("out", "The timestamp (seconds since epoch) of query execution.");
    return true;
}

search::fef::Blueprint::UP
NowBlueprint::createInstance() const
{
    return search::fef::Blueprint::UP(new NowBlueprint());
}

search::fef::FeatureExecutor &
NowBlueprint::createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    int64_t timestamp;
    const fef::Property &prop = env.getProperties().lookup(fef::queryproperties::now::SystemTime::NAME);
    if (prop.found()) {
        timestamp = atoll(prop.get().c_str());
    } else {
        FastOS_Time now;
        now.SetNow();
        timestamp = (int64_t)now.Secs();
    }
    return stash.create<NowExecutor>(timestamp);
}

}}
