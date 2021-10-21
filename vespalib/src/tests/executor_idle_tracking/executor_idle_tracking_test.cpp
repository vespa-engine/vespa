// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/executor_idle_tracking.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

steady_time at(duration d) { return steady_time(d); }

TEST(ThreadIdleTrackingTest, can_track_idle_time) {
    ThreadIdleTracker state;
    EXPECT_FALSE(state.is_idle()); // starts in active state
    state.set_idle(at(50ms));
    EXPECT_TRUE(state.is_idle());
    EXPECT_EQ(count_ms(state.set_active(at(65ms))), 15);
    EXPECT_FALSE(state.is_idle());
    state.set_idle(at(100ms));
    EXPECT_TRUE(state.is_idle());
    EXPECT_EQ(count_ms(state.set_active(at(150ms))), 50);
    EXPECT_FALSE(state.is_idle());
}

TEST(ThreadIdleTrackingTest, redundant_set_idle_is_handled) {
    ThreadIdleTracker state;
    state.set_idle(at(50ms));
    state.set_idle(at(100ms));
    EXPECT_TRUE(state.is_idle());
    EXPECT_EQ(count_ms(state.set_active(at(150ms))), 100);
}

TEST(ThreadIdleTrackingTest, redundant_set_active_is_handled) {
    ThreadIdleTracker state;
    state.set_idle(at(50ms));
    EXPECT_EQ(count_ms(state.set_active(at(150ms))), 100);
    EXPECT_EQ(count_ms(state.set_active(at(200ms))), 0);
    EXPECT_FALSE(state.is_idle());
}

TEST(ThreadIdleTrackingTest, reset_consumes_idle_time_when_idle) {
    ThreadIdleTracker state;
    state.set_idle(at(50ms));
    EXPECT_EQ(count_ms(state.reset(at(100ms))), 50);
    EXPECT_TRUE(state.is_idle());
    EXPECT_EQ(count_ms(state.set_active(at(150ms))), 50);
}

TEST(ThreadIdleTrackingTest, reset_does_nothing_when_active) {
    ThreadIdleTracker state;
    EXPECT_EQ(count_ms(state.reset(at(100ms))), 0);
    EXPECT_FALSE(state.is_idle());
}

TEST(ExecutorIdleTrackingTest, can_calculate_idle_metric) {
    ExecutorIdleTracker state(at(100ms));
    state.was_idle(20ms);
    state.was_idle(5ms);
    state.was_idle(15ms);
    state.was_idle(3ms);
    state.was_idle(7ms); // 50 ms total idle
    EXPECT_EQ(state.reset(at(120ms), 5), 0.5); // 100 ms total time
    EXPECT_EQ(state.reset(at(140ms), 5), 0.0);
    state.was_idle(25ms);
    EXPECT_EQ(state.reset(at(160ms), 5), 0.25);
}

TEST(ExecutorIdleTrackingTest, avoids_idle_above_1) {
    ExecutorIdleTracker state(at(100ms));
    state.was_idle(100ms);
    EXPECT_EQ(state.reset(at(110ms), 1), 1.0);
}

TEST(ExecutorIdleTrackingTest, avoids_division_by_zero) {
    ExecutorIdleTracker state(at(100ms));
    EXPECT_EQ(state.reset(at(100ms), 1), 0.0);
    state.was_idle(10ms);
    EXPECT_EQ(state.reset(at(100ms), 1), 1.0);
}

GTEST_MAIN_RUN_ALL_TESTS()
