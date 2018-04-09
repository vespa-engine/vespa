// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prepare_restart_handler.h"
#include <vespa/searchcore/proton/flushengine/flushengine.h>
#include <vespa/searchcore/proton/flushengine/prepare_restart_flush_strategy.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.prepare_restart_handler");

namespace proton {

PrepareRestartHandler::PrepareRestartHandler(FlushEngine &flushEngine)
    : _flushEngine(flushEngine),
      _mutex(),
      _cond(),
      _running(false)
{
}

bool
PrepareRestartHandler::prepareRestart(const ProtonConfig &protonCfg)
{
    std::unique_lock lock(_mutex);
    if (!_flushEngine.HasThread()) {
        return false;
    }
    if (!_running) {
        performPrepareRestart(protonCfg, lock);
    } else {
        _cond.wait(lock, [this] { return !_running; });
        LOG(info, "prepareRestart(): Waited for another thread performing prepareRestart()");
    }
    return true;
}

namespace {

PrepareRestartFlushStrategy::Config
createPrepareRestartConfig(const PrepareRestartHandler::ProtonConfig &protonCfg)
{
    return PrepareRestartFlushStrategy::Config(protonCfg.flush.preparerestart.replaycost,
                                               protonCfg.flush.preparerestart.replayoperationcost,
                                               protonCfg.flush.preparerestart.writecost);
}

}

void
PrepareRestartHandler::performPrepareRestart(const ProtonConfig &protonCfg, std::unique_lock<std::mutex> &lock)
{
    _running = true;
    lock.unlock();
    auto strategy = std::make_shared<PrepareRestartFlushStrategy>(createPrepareRestartConfig(protonCfg));
    _flushEngine.setStrategy(strategy);
    lock.lock();
    _running = false;
    _cond.notify_all();
}

}
