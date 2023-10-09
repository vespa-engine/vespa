// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "firstphasefeature.h"
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>


using namespace search::fef;

namespace search::features {

void
FirstPhaseExecutor::execute(uint32_t)
{
    outputs().set_number(0, inputs().get_number(0));
}


FirstPhaseBlueprint::FirstPhaseBlueprint() :
    Blueprint("firstPhase")
{
    // empty
}

void
FirstPhaseBlueprint::visitDumpFeatures(const IIndexEnvironment &,
                                       IDumpFeatureVisitor & visitor) const
{
    // havardpe: dumping this is a really bad idea
    visitor.visitDumpFeature(getBaseName());
}

Blueprint::UP
FirstPhaseBlueprint::createInstance() const
{
    return std::make_unique<FirstPhaseBlueprint>();
}

bool
FirstPhaseBlueprint::setup(const IIndexEnvironment & env,
                           const ParameterList &)
{
    if (auto maybe_input = defineInput(indexproperties::rank::FirstPhase::lookup(env.getProperties()),
                                       AcceptInput::ANY))
    {
        describeOutput("score", "The ranking score for first phase.", maybe_input.value());
        return true;
    } else {
        return false;
    }
}

FeatureExecutor &
FirstPhaseBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    return stash.create<FirstPhaseExecutor>();
}


}
