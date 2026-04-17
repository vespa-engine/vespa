// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "first_phase_max_feature.h"
#include <vespa/searchlib/fef/featureexecutor.h>

using search::fef::FeatureExecutor;

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

}
