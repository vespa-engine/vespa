// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/clock.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/fastos/thread.h>

using vespalib::Clock;
using fastos::TimeStamp;


TEST("Test that clock is ticking forward") {

    Clock clock(0.050);
    FastOS_ThreadPool pool(0x10000);
    ASSERT_TRUE(pool.NewThread(clock.getRunnable(), nullptr) != nullptr);
    fastos::SteadyTimeStamp start = clock.getTimeNS();
    std::this_thread::sleep_for(5s);
    fastos::SteadyTimeStamp stop = clock.getTimeNS();
    EXPECT_TRUE(stop > start);
    std::this_thread::sleep_for(6s);
    clock.stop();
    fastos::SteadyTimeStamp stop2 = clock.getTimeNS();
    EXPECT_TRUE(stop2 > stop);
    EXPECT_TRUE((stop2 - stop)/TimeStamp::MICRO > 1000);
}

TEST_MAIN() { TEST_RUN_ALL(); }