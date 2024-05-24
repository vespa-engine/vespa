// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_phase_rank_feature.h"
#include "valuefeature.h"
#include <vespa/vespalib/util/stash.h>

namespace search::features {

FirstPhaseRankExecutor::FirstPhaseRankExecutor(const FirstPhaseRankLookup& lookup)
    : FeatureExecutor(),
      _lookup(lookup)
{
}
FirstPhaseRankExecutor::~FirstPhaseRankExecutor() = default;

void
FirstPhaseRankExecutor::execute(uint32_t docid)
{
    outputs().set_number(0, _lookup.lookup(docid));
}

FirstPhaseRankBlueprint::FirstPhaseRankBlueprint()
    : Blueprint("firstPhaseRank")
{
}

FirstPhaseRankBlueprint::~FirstPhaseRankBlueprint() = default;

void
FirstPhaseRankBlueprint::visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const
{
}

std::unique_ptr<fef::Blueprint>
FirstPhaseRankBlueprint::createInstance() const
{
    return std::make_unique<FirstPhaseRankBlueprint>();
}

fef::ParameterDescriptions
FirstPhaseRankBlueprint::getDescriptions() const
{
    return fef::ParameterDescriptions().desc();
}

bool
FirstPhaseRankBlueprint::setup(const fef::IIndexEnvironment&, const fef::ParameterList&)
{
    describeOutput("score", "The first phase rank.");
    return true;
}

void
FirstPhaseRankBlueprint::prepareSharedState(const fef::IQueryEnvironment&, fef::IObjectStore& store) const
{
    FirstPhaseRankLookup::make_shared_state(store);
}

fef::FeatureExecutor&
FirstPhaseRankBlueprint::createExecutor(const fef::IQueryEnvironment& env, vespalib::Stash& stash) const
{
    const auto* lookup = FirstPhaseRankLookup::get_shared_state(env.getObjectStore());
    if (lookup != nullptr) {
        return stash.create<FirstPhaseRankExecutor>(*lookup);
    } else {
        std::vector<feature_t> values{std::numeric_limits<feature_t>::max()};
        return stash.create<ValueExecutor>(values);
    }
}

}
