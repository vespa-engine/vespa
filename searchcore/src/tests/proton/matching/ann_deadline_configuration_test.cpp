// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/ann_deadline_configuration.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/time.h>
#include <cstdint>

using proton::matching::AnnDeadlineConfiguration;
using vespalib::Deadline;
using vespalib::Doom;
using vespalib::steady_time;

class AnnDeadlineConfigurationTest : public ::testing::Test {
protected:
    std::atomic<steady_time> time;
    Doom doom;

public:
    AnnDeadlineConfigurationTest();
    ~AnnDeadlineConfigurationTest() override;

};

AnnDeadlineConfigurationTest::AnnDeadlineConfigurationTest()
    : time(vespalib::steady_clock::now()),
      doom(time, time.load() + 1s) {
}

AnnDeadlineConfigurationTest::~AnnDeadlineConfigurationTest() = default;

TEST_F(AnnDeadlineConfigurationTest, soft_doom_becomes_deadline) {
    for (auto duration : {50ms, 100ms, 1000ms}) {
        AnnDeadlineConfiguration config(time.load() + duration);

        for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
            Deadline deadline = config.make_ann_deadline(doom, remaining_searches);
            EXPECT_EQ(Deadline::Type::BUDGET, deadline.type());
            EXPECT_EQ(duration, deadline.time_left());
        }
    }
}

TEST_F(AnnDeadlineConfigurationTest, ann_timebudget_becomes_deadline) {
    for (auto budget : {50ms, 100ms, 1000ms}) {
        for (auto ann_timeout : {10ms, 20ms, 70ms, 500ms}) { // timeout disabled, acts as soft-timeout
            AnnDeadlineConfiguration config(budget, false, time.load() + ann_timeout);

            for (uint32_t remaining_searches : {1, 2, 3, 5, 10}) {
                Deadline deadline = config.make_ann_deadline(doom, remaining_searches);
                EXPECT_EQ(Deadline::Type::BUDGET, deadline.type());
                EXPECT_EQ(std::min(budget, ann_timeout), deadline.time_left());
            }
        }
    }
}

TEST_F(AnnDeadlineConfigurationTest, ann_timeout_becomes_deadline) {
    for (auto ann_timeout : {10ms, 20ms, 80ms, 500ms}) { // Less than time budget of 2s
        AnnDeadlineConfiguration config(2s, true, time.load() + ann_timeout);

        for (uint32_t remaining_searches : {1, 2, 5, 10}) {
            Deadline deadline = config.make_ann_deadline(doom, remaining_searches);
            EXPECT_EQ(Deadline::Type::TIMEOUT, deadline.type());
            EXPECT_EQ(ann_timeout / remaining_searches, deadline.time_left());
        }
    }
}

TEST_F(AnnDeadlineConfigurationTest, ann_budget_ann_timeout_interaction_is_handled) {
    AnnDeadlineConfiguration config(50ms, true, time.load() + 100ms);
    Deadline deadline = config.make_ann_deadline(doom, 1);
    EXPECT_EQ(Deadline::Type::BUDGET, deadline.type());
    EXPECT_EQ(50ms, deadline.time_left());

    Deadline deadline2 = config.make_ann_deadline(doom, 2);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline2.type());
    EXPECT_EQ(50ms, deadline2.time_left());

    Deadline deadline3 = config.make_ann_deadline(doom, 4);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline3.type());
    EXPECT_EQ(25ms, deadline3.time_left());


    time.store(time.load() + 50ms);
    Deadline deadline4 = config.make_ann_deadline(doom, 1);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline4.type());
    EXPECT_EQ(50ms, deadline4.time_left());

    Deadline deadline5 = config.make_ann_deadline(doom, 2);
    EXPECT_EQ(Deadline::Type::TIMEOUT, deadline5.type());
    EXPECT_EQ(25ms, deadline5.time_left());
}

GTEST_MAIN_RUN_ALL_TESTS()
