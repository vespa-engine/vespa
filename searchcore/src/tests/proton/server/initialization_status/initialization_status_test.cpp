// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/proton_initialization_status.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::ProtonInitializationStatus;

TEST(AttributeInitializationStatusTest, test_state_to_string)
{
    EXPECT_EQ("initializing", ProtonInitializationStatus::state_to_string(ProtonInitializationStatus::INITIALIZING));
    EXPECT_EQ("ready", ProtonInitializationStatus::state_to_string(ProtonInitializationStatus::READY));
}

TEST(AttributeInitializationStatusTest, test_states)
{
    ProtonInitializationStatus status;
    status.start_initialization();
    EXPECT_EQ(ProtonInitializationStatus::State::INITIALIZING, status.get_state());
    status.end_initialization();
    EXPECT_EQ(ProtonInitializationStatus::State::READY, status.get_state());
}

TEST(ProtonInitializationStatusTest, test_timestamps)
{
    ProtonInitializationStatus::time_point zero;

    ProtonInitializationStatus status;
    status.start_initialization();
    ProtonInitializationStatus::time_point start_time = status.get_start_time();
    EXPECT_GT(start_time, zero);

    status.end_initialization();
    ProtonInitializationStatus::time_point end_time = status.get_end_time();
    EXPECT_GE(end_time, start_time);

    EXPECT_EQ(start_time, status.get_start_time());
    EXPECT_EQ(end_time, status.get_end_time());
}

GTEST_MAIN_RUN_ALL_TESTS()
