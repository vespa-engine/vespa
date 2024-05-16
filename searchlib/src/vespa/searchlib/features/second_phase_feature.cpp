// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "second_phase_feature.h"
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/util/stash.h>

using namespace search::fef;

namespace search::features {

void
SecondPhaseExecutor::execute(uint32_t)
{
    outputs().set_number(0, inputs().get_number(0));
}


SecondPhaseBlueprint::SecondPhaseBlueprint()
    : Blueprint("secondPhase")
{
}

void
SecondPhaseBlueprint::visitDumpFeatures(const IIndexEnvironment&,
                                        IDumpFeatureVisitor&) const
{
}

Blueprint::UP
SecondPhaseBlueprint::createInstance() const
{
    return std::make_unique<SecondPhaseBlueprint>();
}

bool
SecondPhaseBlueprint::setup(const IIndexEnvironment& env,
                            const ParameterList&)
{
    if (auto maybe_input = defineInput(indexproperties::rank::SecondPhase::lookup(env.getProperties()),
                                       AcceptInput::ANY))
    {
        describeOutput("score", "The ranking score for second phase.", maybe_input.value());
        return true;
    } else {
        return false;
    }
}

FeatureExecutor &
SecondPhaseBlueprint::createExecutor(const IQueryEnvironment&, vespalib::Stash& stash) const
{
    return stash.create<SecondPhaseExecutor>();
}

}
