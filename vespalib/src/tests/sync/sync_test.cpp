// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/gate.h>

using namespace vespalib;

TEST("testCountDownLatch") {
    CountDownLatch latch(5);
    EXPECT_EQUAL(latch.getCount(), 5u);
    latch.countDown();
    EXPECT_EQUAL(latch.getCount(), 4u);
    latch.countDown();
    EXPECT_EQUAL(latch.getCount(), 3u);
    latch.countDown();
    EXPECT_EQUAL(latch.getCount(), 2u);
    latch.countDown();
    EXPECT_EQUAL(latch.getCount(), 1u);
    latch.countDown();
    EXPECT_EQUAL(latch.getCount(), 0u);
    latch.countDown();
    EXPECT_EQUAL(latch.getCount(), 0u);
    latch.await(); // should not block
    latch.await(); // should not block
}
TEST("test gate dropping below zero") {
    Gate gate;
    EXPECT_EQUAL(gate.getCount(), 1u);
    gate.countDown();
    EXPECT_EQUAL(gate.getCount(), 0u);
    gate.countDown();
    EXPECT_EQUAL(gate.getCount(), 0u);
    gate.await(); // should not block
    gate.await(); // should not block
}

TEST("test gate non blocking await return correct states") {
    Gate gate;
    EXPECT_EQUAL(gate.getCount(), 1u);
    EXPECT_EQUAL(gate.await(0ms), false);
    EXPECT_EQUAL(gate.await(10ms), false);
    gate.countDown();
    EXPECT_EQUAL(gate.getCount(), 0u);
    EXPECT_EQUAL(gate.await(0ms), true);
    EXPECT_EQUAL(gate.await(10ms), true);
}

TEST_MAIN() { TEST_RUN_ALL(); }
