// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fastos/timestamp.h>
#include <atomic>
#include <memory>

class FastOS_Runnable;

namespace vespalib {

namespace clock::internal { class Updater; }

/**
 * Clock is a clock that updates the time at defined intervals.
 * It is intended used where you want to check the time with low cost, but where
 * resolution is not that important.
 */

class Clock
{
private:
    mutable std::atomic<int64_t>              _timeNS;
    std::unique_ptr<clock::internal::Updater> _updater;
    std::atomic<bool>                         _running;

    void setTime() const;
    void start();
    friend clock::internal::Updater;
public:
    Clock(double timePeriod=0.100);
    ~Clock();

    fastos::SteadyTimeStamp getTimeNS() const {
        if (!_running) {
            setTime();
        }
        return getTimeNSAssumeRunning();
    }
    fastos::SteadyTimeStamp getTimeNSAssumeRunning() const {
        return fastos::SteadyTimeStamp(_timeNS.load(std::memory_order_relaxed));
    }

    void stop();
    FastOS_Runnable * getRunnable();
};

}

