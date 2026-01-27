// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prepare_restart_handler.h"
#include <vespa/searchcore/proton/flushengine/flushengine.h>
#include <vespa/searchcore/proton/flushengine/prepare_restart_flush_strategy.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.prepare_restart_handler");

using proton::flushengine::SetStrategyResult;

namespace proton {

PrepareRestartHandler::PrepareRestartHandler(FlushEngine &flushEngine)
    : _flushEngine(flushEngine)
{
}

namespace {

PrepareRestartFlushStrategy::Config
createPrepareRestartConfig(const PrepareRestartHandler::ProtonConfig &protonCfg)
{
    return PrepareRestartFlushStrategy::Config(protonCfg.flush.preparerestart.replaycost,
                                               protonCfg.flush.preparerestart.replayoperationcost,
                                               protonCfg.flush.preparerestart.writecost,
                                               protonCfg.flush.preparerestart.readcost);
}

}

SetStrategyResult
PrepareRestartHandler::prepare_restart2(const ProtonConfig &protonCfg, uint32_t wait_strategy_id)
{
    if (!_flushEngine.has_thread()) {
        return SetStrategyResult();
    }
    if (wait_strategy_id == 0) {
        return _flushEngine.set_strategy(std::make_shared<PrepareRestartFlushStrategy>(createPrepareRestartConfig(protonCfg)));
    } else {
        return _flushEngine.poll_strategy(wait_strategy_id);
    }
}

}
