// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/clock.h>
#include <vespa/vespalib/util/invokeserviceimpl.h>
#include <thread>

using vespalib::Clock;
using vespalib::duration;
using vespalib::steady_time;
using vespalib::steady_clock;

void waitForMovement(steady_time start, Clock & clock, vespalib::duration timeout) {
    steady_time startOsClock = steady_clock::now();
    while ((clock.getTimeNS() <= start) && ((steady_clock::now() - startOsClock) < timeout)) {
        std::this_thread::sleep_for(1ms);
    }
}

TEST("Test that clock is ticking forward") {
    vespalib::InvokeServiceImpl invoker(50ms);
    Clock clock(invoker.nowRef());
    steady_time start = clock.getTimeNS();
    waitForMovement(start, clock, 10s);
    steady_time stop = clock.getTimeNS();
    EXPECT_TRUE(stop > start);
}

TEST_MAIN() { TEST_RUN_ALL(); }