// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mock_tick.h"

namespace vespalib::metrics {

const std::chrono::seconds oneSec{1};

TimeStamp
MockTick::next(TimeStamp prev)
{
    std::unique_lock<std::mutex> locker(_lock);
    _prevValue = prev;
    while (_runFlag) {
        if (_provided) {
            _blocked.store(false);
            _provided.store(false);
            return _nextValue;
        }
        _blocked.store(true);
        _blockedCond.notify_all();
        auto r = _providedCond.wait_for(locker, oneSec);
        (void)r;
    }
    return TimeStamp(0);
}

void
MockTick::kill()
{
    std::unique_lock<std::mutex> locker(_lock);
    _runFlag.store(false);
    _blockedCond.notify_all();
    _providedCond.notify_all();
}

void
MockTick::provide(TimeStamp value)
{
    std::unique_lock<std::mutex> locker(_lock);
    _nextValue = value;
    _blocked.store(false);
    _provided.store(true);
    _providedCond.notify_all();
}

TimeStamp
MockTick::waitUntilBlocked()
{
    std::unique_lock<std::mutex> locker(_lock);
    while (_runFlag) {
        if (_blocked) {
            return _prevValue;
        }
        auto r = _blockedCond.wait_for(locker, oneSec);
        (void)r;
    }
    return TimeStamp(0);
}

MockTick::MockTick()
    : _lock(),
      _runFlag(true),
      _provided(false),
      _blocked(false),
      _providedCond(),
      _blockedCond(),
      _nextValue(0.0),
      _prevValue(0.0)
{}

} // namespace vespalib::metrics
