// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/ddbstate.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::DDBState;

TEST(DDBStateTest, test_timestamps) {
    DDBState::time_point zero;

    DDBState state;
    state.enterLoadState();

    DDBState::time_point load_time = state.get_load_time();
    EXPECT_GT(load_time, zero);

    state.enterReplayTransactionLogState();
    DDBState::time_point replay_time = state.get_replay_time();
    EXPECT_GE(replay_time, load_time);

    state.enterApplyLiveConfigState();
    state.enterReprocessState();
    state.enterOnlineState();
    DDBState::time_point online_time = state.get_online_time();
    EXPECT_GE(online_time, load_time);

    EXPECT_EQ(load_time, state.get_load_time());
    EXPECT_EQ(replay_time, state.get_replay_time());
    EXPECT_EQ(online_time, state.get_online_time());
}
