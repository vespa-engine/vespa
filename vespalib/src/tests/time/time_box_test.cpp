// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/time/time_box.h>
#include <thread>

TEST(TimeBoxTest, require_that_long_lived_timebox_returns_falling_time_left_numbers) {
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

TEST(TimeBoxTest, require_that_short_lived_timebox_times_out) {
    vespalib::TimeBox box(0.125);
    std::this_thread::sleep_for(std::chrono::milliseconds(150));
    EXPECT_FALSE(box.hasTimeLeft());
    EXPECT_EQ(box.timeLeft(), 0.0);
}

TEST(TimeBoxTest, require_that_short_lived_timebox_always_returns_at_least_minimum_time) {
    vespalib::TimeBox box(0.250, 0.125);
    for (int i = 0; i < 10; i++) {
        double timeLeft = box.timeLeft();
        EXPECT_TRUE(timeLeft <= 0.250);
        EXPECT_TRUE(timeLeft >= 0.125);
        std::this_thread::sleep_for(std::chrono::milliseconds(30));
    }
    EXPECT_FALSE(box.hasTimeLeft());
    EXPECT_EQ(box.timeLeft(), 0.125);
}

GTEST_MAIN_RUN_ALL_TESTS()
