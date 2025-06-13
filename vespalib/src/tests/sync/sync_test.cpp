// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/gate.h>

using namespace vespalib;

TEST(SyncTest, testCountDownLatch) {
    CountDownLatch latch(5);
    EXPECT_EQ(latch.getCount(), 5u);
    latch.countDown();
    EXPECT_EQ(latch.getCount(), 4u);
    latch.countDown();
    EXPECT_EQ(latch.getCount(), 3u);
    latch.countDown();
    EXPECT_EQ(latch.getCount(), 2u);
    latch.countDown();
    EXPECT_EQ(latch.getCount(), 1u);
    latch.countDown();
    EXPECT_EQ(latch.getCount(), 0u);
    latch.countDown();
    EXPECT_EQ(latch.getCount(), 0u);
    latch.await(); // should not block
    latch.await(); // should not block
}
TEST(SyncTest, test_gate_dropping_below_zero) {
    Gate gate;
    EXPECT_EQ(gate.getCount(), 1u);
    gate.countDown();
    EXPECT_EQ(gate.getCount(), 0u);
    gate.countDown();
    EXPECT_EQ(gate.getCount(), 0u);
    gate.await(); // should not block
    gate.await(); // should not block
}

TEST(SyncTest, test_gate_non_blocking_await_return_correct_states) {
    Gate gate;
    EXPECT_EQ(gate.getCount(), 1u);
    EXPECT_EQ(gate.await(0ms), false);
    EXPECT_EQ(gate.await(10ms), false);
    gate.countDown();
    EXPECT_EQ(gate.getCount(), 0u);
    EXPECT_EQ(gate.await(0ms), true);
    EXPECT_EQ(gate.await(10ms), true);
}

GTEST_MAIN_RUN_ALL_TESTS()
