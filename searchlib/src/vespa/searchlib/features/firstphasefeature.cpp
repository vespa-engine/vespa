// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "firstphasefeature.h"
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/properties.h>

using namespace search::fef;

namespace search {
namespace features {

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
    return Blueprint::UP(new FirstPhaseBlueprint());
}

bool
FirstPhaseBlueprint::setup(const IIndexEnvironment & env,
                           const ParameterList &)
{
    describeOutput("score", "The ranking score for first phase.",
                   defineInput(indexproperties::rank::FirstPhase::lookup(env.getProperties()),
                               AcceptInput::ANY));
    return true;
}

FeatureExecutor &
FirstPhaseBlueprint::createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const
{
    return stash.create<FirstPhaseExecutor>();
}


} // namespace features
} // namespace search
