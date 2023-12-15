// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "time.h"
#include <atomic>

namespace vespalib {

class InvokeServiceImpl;

/**
 * Self contained clock useable for testing that provides a backing for the vespalib::Clock interface.
 */

class TestClock
{
private:
    std::unique_ptr<InvokeServiceImpl> _ticker;
public:
    TestClock();
    TestClock(const TestClock &) = delete;
    TestClock & operator =(const TestClock &) = delete;
    TestClock(TestClock &&) = delete;
    TestClock & operator =(TestClock &&) = delete;
    ~TestClock();
    const std::atomic<steady_time> & nowRef() const;
};

}

