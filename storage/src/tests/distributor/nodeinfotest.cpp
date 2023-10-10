// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/nodeinfo.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

TEST(NodeInfoTest, simple) {
    framework::defaultimplementation::FakeClock clock;
    NodeInfo info(clock);

    EXPECT_EQ(0, info.getPendingCount(3));
    EXPECT_EQ(0, info.getPendingCount(9));

    info.incPending(3);
    info.incPending(3);
    info.incPending(3);
    info.incPending(3);
    info.decPending(3);
    info.decPending(4);
    info.incPending(7);
    info.incPending(4);
    info.decPending(3);

    EXPECT_EQ(2, info.getPendingCount(3));
    EXPECT_EQ(1, info.getPendingCount(4));
    EXPECT_EQ(1, info.getPendingCount(7));
    EXPECT_EQ(0, info.getPendingCount(5));

    info.setBusy(5, std::chrono::seconds(60));
    clock.addSecondsToTime(10);
    info.setBusy(1, std::chrono::seconds(60));
    clock.addSecondsToTime(20);
    info.setBusy(42, std::chrono::seconds(60));

    EXPECT_TRUE(info.isBusy(5));
    EXPECT_TRUE(info.isBusy(1));
    EXPECT_TRUE(info.isBusy(42));
    EXPECT_FALSE(info.isBusy(7));

    clock.addSecondsToTime(42);

    EXPECT_FALSE(info.isBusy(5));
    EXPECT_FALSE(info.isBusy(1));
    EXPECT_TRUE(info.isBusy(42));
    EXPECT_FALSE(info.isBusy(7));

}

}
