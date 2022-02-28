// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <atomic>
#include <memory>

namespace vespalib {

class IDestructorCallback;
class InvokeService;

/**
 * Clock is a clock that updates the time at defined intervals.
 * It is intended used where you want to check the time with low cost, but where
 * resolution is not that important.
 */

class Clock
{
private:
    mutable std::atomic<int64_t>              _timeNS;
    std::atomic<bool>                         _running;
    std::unique_ptr<IDestructorCallback>      _invokeRegistration;

    void setTime() const;
public:
    Clock();
    ~Clock();

    vespalib::steady_time getTimeNS() const {
        if (!_running) {
            setTime();
        }
        return getTimeNSAssumeRunning();
    }
    vespalib::steady_time getTimeNSAssumeRunning() const {
        return vespalib::steady_time(std::chrono::nanoseconds(_timeNS.load(std::memory_order_relaxed)));
    }

    void start(InvokeService & invoker);
    void stop();
};

}

