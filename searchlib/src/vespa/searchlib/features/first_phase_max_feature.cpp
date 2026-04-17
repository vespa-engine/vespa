// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_phase_max_feature.h"

#include <vespa/searchlib/features/valuefeature.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/vespalib/util/stash.h>

#include <memory>

using search::fef::Blueprint;
using search::fef::FeatureExecutor;
using search::fef::IDumpFeatureVisitor;
using search::fef::IIndexEnvironment;
using search::fef::IObjectStore;
using search::fef::IQueryEnvironment;
using search::fef::ParameterDescriptions;
using search::fef::ParameterList;
using vespalib::Stash;

namespace search::features {

// -------------- Executor -----------------------

FirstPhaseMaxExecutor::FirstPhaseMaxExecutor(const FirstPhaseMax& max)
    : FeatureExecutor(), _max(max) {
}

FirstPhaseMaxExecutor::~FirstPhaseMaxExecutor() = default;

void FirstPhaseMaxExecutor::execute(uint32_t) {
    outputs().set_number(0, _max.get());
}


// -------------- Blueprint -----------------------

FirstPhaseMaxBlueprint::FirstPhaseMaxBlueprint()
    : Blueprint("firstPhaseMax") {
}

FirstPhaseMaxBlueprint::~FirstPhaseMaxBlueprint() = default;

bool FirstPhaseMaxBlueprint::setup(const IIndexEnvironment&, const ParameterList&) {
    describeOutput("score", "The max score from first phase.");
    return true;
}

FeatureExecutor& FirstPhaseMaxBlueprint::createExecutor(const IQueryEnvironment& env, Stash& stash) const {
    const auto* max = FirstPhaseMax::get_shared_state(env.getObjectStore());
    if (max != nullptr) {
        return stash.create<FirstPhaseMaxExecutor>(*max);
    } else {
        std::vector<feature_t> values{-std::numeric_limits<feature_t>::infinity()};
        return stash.create<ValueExecutor>(values);
    }
}

ParameterDescriptions FirstPhaseMaxBlueprint::getDescriptions() const {
    return ParameterDescriptions().desc();
}

std::unique_ptr<Blueprint> FirstPhaseMaxBlueprint::createInstance() const {
    return std::make_unique<FirstPhaseMaxBlueprint>();
}

void FirstPhaseMaxBlueprint::prepareSharedState(const IQueryEnvironment&, IObjectStore& store) const {
    FirstPhaseMax::make_shared_state(store);
}

void FirstPhaseMaxBlueprint::visitDumpFeatures(const IIndexEnvironment&, IDumpFeatureVisitor&) const {
    // no-op
}

}
