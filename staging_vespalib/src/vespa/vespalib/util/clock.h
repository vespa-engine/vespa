// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <atomic>
#include <memory>

namespace vespalib {

/**
 * Clock is a clock that updates the time at defined intervals.
 * It is intended used where you want to check the time with low cost, but where
 * resolution is not that important.
 */

class Clock
{
private:
    const std::atomic<steady_time> &_timeNS;
public:
    Clock(const std::atomic<steady_time> & source) noexcept;
    Clock(const Clock &) = delete;
    Clock & operator =(const Clock &) = delete;
    Clock(Clock &&) = delete;
    Clock & operator =(Clock &&) = delete;
    ~Clock();

    vespalib::steady_time getTimeNS() const noexcept {
        return vespalib::steady_time(_timeNS.load(std::memory_order_relaxed));
    }
};

}
