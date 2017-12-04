// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "clock.h"

#include <atomic>
#include <condition_variable>
#include <mutex>

namespace vespalib::metrics {

// internal
class SimpleTick : public Tick {
private:
    std::mutex _lock;
    std::atomic<bool> _runFlag;
    std::condition_variable _cond;
public:
    SimpleTick();
    TimeStamp next(TimeStamp prev) override;
    void kill() override;
    bool alive() override;
};

} // namespace vespalib::metrics
