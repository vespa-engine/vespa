// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "clock.h"

#include <atomic>
#include <condition_variable>
#include <mutex>

namespace vespalib::metrics {

// share the MockTick between the tested and the tester.
class MockTick : public Tick {
private:
    std::mutex _lock;
    std::atomic<bool> _runFlag;
    std::atomic<bool> _provided;
    std::atomic<bool> _blocked;
    std::condition_variable _providedCond;
    std::condition_variable _blockedCond;
    TimeStamp _nextValue;
    TimeStamp _prevValue;
public:
    MockTick();
    TimeStamp next(TimeStamp prev) override;
    void kill() override;

    void provide(TimeStamp value);
    TimeStamp waitUntilBlocked();
};

} // namespace vespalib::metrics
