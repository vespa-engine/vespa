// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simple_tick.h"

namespace vespalib::metrics {

namespace {

const TimeStamp oneSec{1.0};

TimeStamp now()
{
    using Clock = std::chrono::system_clock;
    Clock::time_point now = Clock::now();
    return now.time_since_epoch();
}

} // namespace <unnamed>

SimpleTick::SimpleTick()
    : _lock(), _runFlag(true), _cond()
{}

TimeStamp
SimpleTick::next(TimeStamp prev)
{
    std::unique_lock<std::mutex> locker(_lock);
    while (_runFlag) {
        TimeStamp curr = now();
        if (curr - prev >= oneSec) {
            return curr;
        }
        if (curr <= prev) {
            _cond.wait_for(locker, oneSec);
        } else {
            _cond.wait_for(locker, oneSec - (curr - prev));
        }
    }
    return now();
}

void
SimpleTick::kill()
{
    std::unique_lock<std::mutex> locker(_lock);
    _runFlag.store(false);
    _cond.notify_all();
}

bool
SimpleTick::alive()
{
    std::unique_lock<std::mutex> locker(_lock);
    return _runFlag;
}

} // namespace vespalib::metrics
