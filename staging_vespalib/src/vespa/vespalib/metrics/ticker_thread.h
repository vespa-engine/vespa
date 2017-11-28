// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <condition_variable>
#include <mutex>
#include <thread>

namespace vespalib {
namespace metrics {

class SimpleMetricsManager;

class TickerThread {
private:
    SimpleMetricsManager *_owner;
    std::mutex _lock;
    std::atomic<bool> _runFlag;
    std::condition_variable _cond;
    std::thread _thread;

    void tickerLoop();
public:
    TickerThread(SimpleMetricsManager * owner)
        : _owner(owner),
          _runFlag(true),
          _thread(&TickerThread::tickerLoop, this)
    {}
    ~TickerThread() { stop(); }

    void stop();
};

} // namespace vespalib::metrics
} // namespace vespalib
