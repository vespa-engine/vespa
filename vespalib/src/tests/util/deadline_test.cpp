// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/deadline.h>
#include <vespa/vespalib/util/time.h>

using vespalib::Deadline;
using vespalib::steady_time;

class DeadlineTest : public ::testing::Test {
protected:
    std::atomic<steady_time> time;

public:
    DeadlineTest();
    ~DeadlineTest() override;

};

DeadlineTest::DeadlineTest()
    : time(vespalib::steady_clock::now()) {
}

DeadlineTest::~DeadlineTest() = default;

TEST_F(DeadlineTest, deadline_matches_time_left) {
    Deadline deadline(time, time.load() + 50ms, Deadline::BUDGET);

    EXPECT_EQ(50ms, deadline.time_left());
    EXPECT_FALSE(deadline.is_missed());

    time.store(time.load() + 10ms);
    EXPECT_EQ(40ms, deadline.time_left());
    EXPECT_FALSE(deadline.is_missed());

    time.store(time.load() + 40ms);
    EXPECT_EQ(0ms, deadline.time_left());
    EXPECT_FALSE(deadline.is_missed());

    time.store(time.load() + 1ms);
    EXPECT_EQ(-1ms, deadline.time_left());
    EXPECT_TRUE(deadline.is_missed());
}

TEST_F(DeadlineTest, deadline_remembers_if_it_was_missed) {
    Deadline deadline(time, time.load() + 50ms, Deadline::BUDGET);

    EXPECT_EQ(50ms, deadline.time_left());
    EXPECT_FALSE(deadline.is_missed());
    EXPECT_FALSE(deadline.was_missed());

    time.store(time.load() + 10ms);
    EXPECT_EQ(40ms, deadline.time_left());
    EXPECT_FALSE(deadline.is_missed());
    EXPECT_FALSE(deadline.was_missed());

    time.store(time.load() + 40ms);
    EXPECT_EQ(0ms, deadline.time_left());
    EXPECT_FALSE(deadline.is_missed());
    EXPECT_FALSE(deadline.was_missed());

    time.store(time.load() + 1ms);
    EXPECT_EQ(-1ms, deadline.time_left());
    EXPECT_FALSE(deadline.was_missed());
    EXPECT_TRUE(deadline.is_missed());
    EXPECT_TRUE(deadline.was_missed());
}
