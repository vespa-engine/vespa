// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nowfeature.h"
#include <vespa/searchlib/fef/queryproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>
#include <chrono>

namespace search::features {

NowExecutor::NowExecutor(int64_t timestamp) :
    fef::FeatureExecutor(),
    _timestamp(timestamp)
{
}

void
NowExecutor::execute(uint32_t) {
    outputs().set_number(0, _timestamp);
}

void
NowBlueprint::visitDumpFeatures(const fef::IIndexEnvironment &, fef::IDumpFeatureVisitor &) const
{
}

bool
NowBlueprint::setup(const fef::IIndexEnvironment &, const fef::ParameterList &)
{
    describeOutput("out", "The timestamp (seconds since epoch) of query execution.");
    return true;
}

fef::Blueprint::UP
NowBlueprint::createInstance() const
{
    return std::make_unique<NowBlueprint>();
}

using namespace std::chrono;

fef::FeatureExecutor &
NowBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    int64_t timestamp;
    const fef::Property &prop = env.getProperties().lookup(fef::queryproperties::now::SystemTime::NAME);
    if (prop.found()) {
        timestamp = atoll(prop.get().c_str());
    } else {
        timestamp = duration_cast<seconds>(system_clock::now().time_since_epoch()).count();
    }
    return stash.create<NowExecutor>(timestamp);
}

}
