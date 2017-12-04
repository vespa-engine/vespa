// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <atomic>
#include <condition_variable>
#include <mutex>
#include <vespa/vespalib/metrics/clock.h>

namespace vespalib::metrics {

// share the MockTick between the tested and the tester.
class MockTick : public Tick {
private:
    std::mutex _lock;
    bool _runFlag;
    bool _provided;
    bool _blocked;
    std::condition_variable _providedCond;
    std::condition_variable _blockedCond;
    TimeStamp _nextValue;
    TimeStamp _prevValue;
public:
    MockTick();
    TimeStamp next(TimeStamp prev) override;
    void kill() override;
    bool alive() override;

    void provide(TimeStamp value);
    TimeStamp waitUntilBlocked();
};

struct TickProxy : Tick {
    std::shared_ptr<Tick> tick;
    TickProxy(std::shared_ptr<Tick> tick_in) : tick(std::move(tick_in)) {}
    TimeStamp next(TimeStamp prev) override { return tick->next(prev); }
    void kill() override { tick->kill(); }
    bool alive() override { return tick->alive(); }
};

} // namespace vespalib::metrics
