// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/time.h>
#include <cstdint>

using namespace vespalib;

class DoomTest : public ::testing::Test {
protected:
    std::atomic<steady_time> time;

public:
    DoomTest();
    ~DoomTest() override;

};

DoomTest::DoomTest()
    : time(steady_clock::now()) {
}

DoomTest::~DoomTest() = default;

TEST_F(DoomTest, deadline_matches_ann_left) {
    Doom doom(time, time.load() + 50ms);
    Deadline deadline = doom.make_ann_doom(1);

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

TEST_F(DoomTest, doom_becomes_deadline) {
    for (auto duration : {50ms, 100ms, 1000ms}) {
        Doom doom(time, time.load() + duration);

        for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
            Deadline deadline = doom.make_ann_doom(remaining_searches);
            EXPECT_EQ(Deadline::Type::BUDGET, deadline.type());
            EXPECT_EQ(duration, deadline.time_left());
        }
    }
}

TEST_F(DoomTest, soft_doom_becomes_deadline) {
    for (auto duration : {50ms, 100ms, 1000ms}) {
        for (bool explicit_soft_doom : {true, false}) {
            Doom doom(time, time.load() + duration, time.load() + 1s, explicit_soft_doom);

            for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
                Deadline deadline = doom.make_ann_doom(remaining_searches);
                EXPECT_EQ(Deadline::Type::BUDGET, deadline.type());
                EXPECT_EQ(duration, deadline.time_left());
            }
        }
    }
}

TEST_F(DoomTest, ann_timebudget_becomes_deadline) {
    for (auto budget : {50ms, 100ms, 1000ms}) {
        for (auto ann_timeout : {10ms, 20ms, 70ms, 500ms}) { // timeout disabled, this must not have an effect
            for (bool explicit_soft_doom : {true, false}) {
                Doom doom(time, time.load() + 1s, time.load() + 2s, explicit_soft_doom,
                          budget, false, time.load() + ann_timeout);

                for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
                    Deadline deadline = doom.make_ann_doom(remaining_searches);
                    EXPECT_EQ(Deadline::Type::BUDGET, deadline.type());
                    EXPECT_EQ(budget, deadline.time_left());
                }
            }
        }
    }
}

TEST_F(DoomTest, ann_timeout_becomes_deadline) {
    for (auto ann_timeout : {10ms, 20ms, 80ms, 500ms}) { // Less than time budget of 2s
        for (bool explicit_soft_doom : {true, false}) {
            Doom doom(time, time.load() + 1s, time.load() + 2s, explicit_soft_doom,
                      2s, true, time.load() + ann_timeout);

            for (uint32_t remaining_searches : {1, 2, 5, 10}) {
                Deadline deadline = doom.make_ann_doom(remaining_searches);
                EXPECT_EQ(Deadline::Type::TIMEOUT, deadline.type());
                EXPECT_EQ(ann_timeout / remaining_searches, deadline.time_left());
            }
        }
    }
}

TEST_F(DoomTest, ann_budget_ann_timeout_interaction_is_handled) {
    Doom doom(time, time.load() + 1s, time.load() + 2s, false,
              50ms, true, time.load() + 100ms);
    Deadline deadline = doom.make_ann_doom(1);
    EXPECT_EQ(Deadline::Type::BUDGET, deadline.type());
    EXPECT_EQ(50ms, deadline.time_left());

    Deadline deadline2 = doom.make_ann_doom(2);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline2.type());
    EXPECT_EQ(50ms, deadline2.time_left());

    Deadline deadline3 = doom.make_ann_doom(4);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline3.type());
    EXPECT_EQ(25ms, deadline3.time_left());


    time.store(time.load() + 50ms);
    Deadline deadline4 = doom.make_ann_doom(1);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline4.type());
    EXPECT_EQ(50ms, deadline4.time_left());

    Deadline deadline5 = doom.make_ann_doom(2);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline5.type());
    EXPECT_EQ(25ms, deadline5.time_left());
}
