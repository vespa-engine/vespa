// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/operation_rate_tracker.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/time.h>

#include <vespa/log/log.h>
LOG_SETUP("operation_rate_tracker_test");

using namespace proton;

TEST(OperationRateTrackerTest, time_budget_per_op_is_inverse_of_rate_threshold)
{
    EXPECT_EQ(vespalib::from_s(0.25), OperationRateTracker(4).get_time_budget_per_op());
    EXPECT_EQ(vespalib::from_s(2.0), OperationRateTracker(0.5).get_time_budget_per_op());
}

TEST(OperationRateTrackerTest, time_budget_window_is_minimum_1_sec)
{
    EXPECT_EQ(vespalib::from_s(1.0), OperationRateTracker(4).get_time_budget_window());
    EXPECT_EQ(vespalib::from_s(2.0), OperationRateTracker(0.5).get_time_budget_window());
}

class Simulator {
public:
    vespalib::steady_time now;
    OperationRateTracker ort;
    Simulator(double rate_threshold)
        : now(vespalib::steady_clock::now()),
          ort(rate_threshold)
    {
    }
    void tick(double real_rate) {
        now = now + vespalib::from_s(1.0 / real_rate);
        ort.observe(now);
    }
    bool above_threshold(double now_delta = 0) {
        return ort.above_threshold(now + vespalib::from_s(now_delta));
    }
};

TEST(OperationRateTrackerTest, tracks_whether_operation_rate_is_below_or_above_threshold)
{
    Simulator sim(2);

    // Simulate an actual rate of 4 ops / sec
    sim.tick(4); // Threshold time is 1.0s in the past (at time budget window start)
    EXPECT_FALSE(sim.above_threshold(-1.0));
    EXPECT_TRUE(sim.above_threshold(-1.01));

    // Catch up with now
    sim.tick(4);
    sim.tick(4);
    sim.tick(4);
    sim.tick(4); // Threshold time is now.
    EXPECT_FALSE(sim.above_threshold());
    EXPECT_TRUE(sim.above_threshold(-0.01));

    // Move into the future
    sim.tick(4); // Threshold time is 0.25s into the future.
    EXPECT_TRUE(sim.above_threshold(0.24));
    EXPECT_FALSE(sim.above_threshold(0.25));

    // Move to time budget window end
    sim.tick(4);
    sim.tick(4);
    sim.tick(4); // Threshold time is 1.0s into the future (at time budget window end)
    EXPECT_TRUE(sim.above_threshold(0.99));
    EXPECT_FALSE(sim.above_threshold(1.0));

    sim.tick(4); // Threshold time is still 1.0s into the future (at time budget window end)
    EXPECT_TRUE(sim.above_threshold(0.99));
    EXPECT_FALSE(sim.above_threshold(1.0));

    // Reduce actual rate to 1 ops / sec
    sim.tick(1); // Threshold time is 0.5s into the future.
    EXPECT_TRUE(sim.above_threshold(0.49));
    EXPECT_FALSE(sim.above_threshold(0.5));

    sim.tick(1); // Threshold time is now.
    EXPECT_FALSE(sim.above_threshold());
    EXPECT_TRUE(sim.above_threshold(-0.01));

    sim.tick(1);
    sim.tick(1); // Threshold time is back at time budget window start
    EXPECT_FALSE(sim.above_threshold(-1.0));
    EXPECT_TRUE(sim.above_threshold(-1.01));
}

GTEST_MAIN_RUN_ALL_TESTS()
