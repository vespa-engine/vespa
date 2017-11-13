// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "ticker_thread.h"
#include "simple_metrics_manager.h"
#include <chrono>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.metrics.ticker_thread");

namespace vespalib {
namespace metrics {

void
TickerThread::doTickerLoop(TickerThread *me)
{
    me->tickerLoop();
}

void
TickerThread::tickerLoop()
{
    const std::chrono::seconds oneSec{1};
    std::unique_lock<std::mutex> locker(_lock);
    while (_runFlag) {
        auto r = _cond.wait_for(locker, oneSec);
        if (r == std::cv_status::timeout) {
            _owner->tick();
        }
    }
}

void
TickerThread::stop()
{
    std::unique_lock<std::mutex> locker(_lock);
    _runFlag.store(false);
    _cond.notify_all();
    locker.unlock();
    _thread.join();
}


} // namespace vespalib::metrics
} // namespace vespalib
