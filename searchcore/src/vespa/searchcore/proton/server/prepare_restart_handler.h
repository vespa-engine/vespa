// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config-proton.h>
#include <vespa/searchcore/proton/flushengine/set_strategy_result.h>
#include <condition_variable>
#include <memory>
#include <mutex>

namespace proton {

class FlushEngine;

/**
 * Class ensuring that only a single prepare restart happens at the same time.
 *
 * If another thread tries to start a new prepare restart while one is running, this thread waits
 * until the ongoing operation is done and returns successfully. No extra work is done.
 */
class PrepareRestartHandler {
public:
    using ProtonConfig = vespa::config::search::core::internal::InternalProtonType;

private:
    FlushEngine &_flushEngine;

public:
    PrepareRestartHandler(FlushEngine &flushEngine);
    flushengine::SetStrategyResult prepare_restart2(const ProtonConfig &protonCfg, uint32_t wait_strategy_id);
};

}
