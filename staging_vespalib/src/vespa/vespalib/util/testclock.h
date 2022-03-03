// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "clock.h"

namespace vespalib {

class InvokeServiceImpl;

/**
 * Clock is a clock that updates the time at defined intervals.
 * It is intended used where you want to check the time with low cost, but where
 * resolution is not that important.
 */

class TestClock
{
private:
    std::unique_ptr<InvokeServiceImpl> _ticker;
    Clock _clock;
public:
    TestClock();
    TestClock(const TestClock &) = delete;
    TestClock & operator =(const TestClock &) = delete;
    TestClock(TestClock &&) = delete;
    TestClock & operator =(TestClock &&) = delete;
    ~TestClock();
    const Clock & clock() { return _clock; }
};

}

