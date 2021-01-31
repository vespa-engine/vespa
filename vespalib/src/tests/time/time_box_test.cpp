// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/time/time_box.h>
#include <thread>

TEST("require that long-lived timebox returns falling time left numbers") {
    vespalib::TimeBox box(3600);
    double last_timeLeft = box.timeLeft();
    for (int i = 0; i < 10; i++) {
        EXPECT_TRUE(box.hasTimeLeft());
        double timeLeft = box.timeLeft();
        EXPECT_TRUE(timeLeft <= last_timeLeft);
        last_timeLeft = timeLeft;
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

TEST("require that short-lived timebox times out") {
    vespalib::TimeBox box(0.125);
    std::this_thread::sleep_for(std::chrono::milliseconds(150));
    EXPECT_FALSE(box.hasTimeLeft());
    EXPECT_EQUAL(box.timeLeft(), 0.0);
}

TEST("require that short-lived timebox always returns at least minimum time") {
    vespalib::TimeBox box(0.250, 0.125);
    for (int i = 0; i < 10; i++) {
        double timeLeft = box.timeLeft();
        EXPECT_TRUE(timeLeft <= 0.250);
        EXPECT_TRUE(timeLeft >= 0.125);
        std::this_thread::sleep_for(std::chrono::milliseconds(30));
    }
    EXPECT_FALSE(box.hasTimeLeft());
    EXPECT_EQUAL(box.timeLeft(), 0.125);
}

TEST_MAIN() { TEST_RUN_ALL(); }
