// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "first_phase_max.h"

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::features {

/**
 * Executor for first phase max feature which outputs the max score from first
 * phase.
 */
class FirstPhaseMaxExecutor : public fef::FeatureExecutor {
    const FirstPhaseMax& _max;

public:
    explicit FirstPhaseMaxExecutor(const FirstPhaseMax& max);
    ~FirstPhaseMaxExecutor() override;

    void execute(uint32_t) override;
};

/**
 * Blueprint for first phase max feature.
 */
class FirstPhaseMaxBlueprint : public fef::Blueprint {
public:

};


} // namespace search::features

