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

TEST_F(DoomTest, ann_doom_matches_ann_left) {
    Doom doom(time, time.load() + 50ms);
    AnnDoom ann_doom = doom.make_ann_doom(1);

    EXPECT_EQ(50ms, ann_doom.ann_left());
    EXPECT_FALSE(ann_doom.ann_doom());

    time.store(time.load() + 10ms);
    EXPECT_EQ(40ms, ann_doom.ann_left());
    EXPECT_FALSE(ann_doom.ann_doom());

    time.store(time.load() + 40ms);
    EXPECT_EQ(0ms, ann_doom.ann_left());
    EXPECT_FALSE(ann_doom.ann_doom());

    time.store(time.load() + 1ms);
    EXPECT_EQ(-1ms, ann_doom.ann_left());
    EXPECT_TRUE(ann_doom.ann_doom());
}

TEST_F(DoomTest, doom_becomes_ann_doom) {
    for (auto duration : {50ms, 100ms, 1000ms}) {
        Doom doom(time, time.load() + duration);

        for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
            AnnDoom ann_doom = doom.make_ann_doom(remaining_searches);
            EXPECT_FALSE(ann_doom.is_ann_timeout());
            EXPECT_EQ(duration, ann_doom.ann_left());
        }
    }
}

TEST_F(DoomTest, soft_doom_becomes_ann_doom) {
    for (auto duration : {50ms, 100ms, 1000ms}) {
        for (bool explicit_soft_doom : {true, false}) {
            Doom doom(time, time.load() + duration, time.load() + 1s, explicit_soft_doom);

            for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
                AnnDoom ann_doom = doom.make_ann_doom(remaining_searches);
                EXPECT_FALSE(ann_doom.is_ann_timeout());
                EXPECT_EQ(duration, ann_doom.ann_left());
            }
        }
    }
}

TEST_F(DoomTest, ann_timebudget_becomes_ann_doom) {
    for (auto budget : {50ms, 100ms, 1000ms}) {
        for (auto ann_timeout : {10ms, 20ms, 70ms, 500ms}) { // timeout disabled, this must not have an effect
            for (bool explicit_soft_doom : {true, false}) {
                Doom doom(time, time.load() + 1s, time.load() + 2s, explicit_soft_doom,
                          budget, false, time.load() + ann_timeout);

                for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
                    AnnDoom ann_doom = doom.make_ann_doom(remaining_searches);
                    EXPECT_FALSE(ann_doom.is_ann_timeout());
                    EXPECT_EQ(budget, ann_doom.ann_left());
                }
            }
        }
    }
}

TEST_F(DoomTest, ann_timeout_becomes_ann_doom) {
    for (auto ann_timeout : {10ms, 20ms, 80ms, 500ms}) { // Less than time budget of 2s
        for (bool explicit_soft_doom : {true, false}) {
            Doom doom(time, time.load() + 1s, time.load() + 2s, explicit_soft_doom,
                      2s, true, time.load() + ann_timeout);

            for (uint32_t remaining_searches : {1, 2, 5, 10}) {
                AnnDoom ann_doom = doom.make_ann_doom(remaining_searches);
                EXPECT_TRUE(ann_doom.is_ann_timeout());
                EXPECT_EQ(ann_timeout / remaining_searches, ann_doom.ann_left());
            }
        }
    }
}

TEST_F(DoomTest, ann_budget_ann_timeout_interaction_is_handled) {
    Doom doom(time, time.load() + 1s, time.load() + 2s, false,
              50ms, true, time.load() + 100ms);
    AnnDoom ann_doom = doom.make_ann_doom(1);
    EXPECT_FALSE(ann_doom.is_ann_timeout());
    EXPECT_EQ(50ms, ann_doom.ann_left());

    AnnDoom ann_doom2 = doom.make_ann_doom(2);
    EXPECT_TRUE(ann_doom2.is_ann_timeout());
    EXPECT_EQ(50ms, ann_doom2.ann_left());

    AnnDoom ann_doom3 = doom.make_ann_doom(4);
    EXPECT_TRUE(ann_doom3.is_ann_timeout());
    EXPECT_EQ(25ms, ann_doom3.ann_left());


    time.store(time.load() + 50ms);
    AnnDoom ann_doom4 = doom.make_ann_doom(1);
    EXPECT_TRUE(ann_doom4.is_ann_timeout());
    EXPECT_EQ(50ms, ann_doom4.ann_left());

    AnnDoom ann_doom5 = doom.make_ann_doom(2);
    EXPECT_TRUE(ann_doom5.is_ann_timeout());
    EXPECT_EQ(25ms, ann_doom5.ann_left());
}
