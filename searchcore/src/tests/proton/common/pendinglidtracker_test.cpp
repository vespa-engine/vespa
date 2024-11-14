// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/pendinglidtracker.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace proton;

constexpr uint32_t LID_1 = 1u;
const std::vector<uint32_t> LIDV_2_1_3({2u, LID_1, 3u});
const std::vector<uint32_t> LIDV_2_3({2u, 3u});

namespace proton {

std::ostream &
operator << (std::ostream & os, ILidCommitState::State state) {
    switch (state) {
    case ILidCommitState::State::NEED_COMMIT:
        os << "NEED_COMMIT";
        break;
    case ILidCommitState::State::WAITING:
        os << "WAITING";
        break;
    case ILidCommitState::State::COMPLETED:
        os << "COMPLETED";
        break;
    }
    return os;
}

}

void
verifyPhase1ProduceAndNeedCommit(PendingLidTrackerBase & tracker, ILidCommitState::State expected) {
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LID_1));
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_1_3));

    auto token = tracker.produce(LID_1);
    EXPECT_EQ(expected, tracker.getState(LID_1));
    EXPECT_EQ(expected, tracker.getState(LIDV_2_1_3));
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_3));
    {
        auto token2 = tracker.produce(LID_1);
        EXPECT_EQ(expected, tracker.getState(LID_1));
        EXPECT_EQ(expected, tracker.getState(LIDV_2_1_3));
        EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_3));
    }
    EXPECT_EQ(expected, tracker.getState(LID_1));
    EXPECT_EQ(expected, tracker.getState(LIDV_2_1_3));
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_3));
}

TEST(PendingLidTrackerTest, test_pendinglidtracker_for_needcommit)
{
    PendingLidTracker tracker;
    verifyPhase1ProduceAndNeedCommit(tracker, ILidCommitState::State::WAITING);
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LID_1));
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_1_3));
    {
        ILidCommitState::State incomplete = ILidCommitState::State::WAITING;
        auto token = tracker.produce(LID_1);
        EXPECT_EQ(incomplete, tracker.getState(LID_1));
        EXPECT_EQ(incomplete, tracker.getState(LIDV_2_1_3));
        EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_3));
        {
            auto snapshot = tracker.produceSnapshot();
            EXPECT_EQ(incomplete, tracker.getState(LID_1));
            EXPECT_EQ(incomplete, tracker.getState(LIDV_2_1_3));
            EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_3));
        }
        EXPECT_EQ(incomplete, tracker.getState(LID_1));
        EXPECT_EQ(incomplete, tracker.getState(LIDV_2_1_3));
        EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_3));
    }
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LID_1));
    EXPECT_EQ(ILidCommitState::State::COMPLETED, tracker.getState(LIDV_2_1_3));
}
