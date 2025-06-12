// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/slobrok/backoff.h>

#include <vespa/log/log.h>
LOG_SETUP("backoff_test");

using slobrok::api::BackOff;

//-----------------------------------------------------------------------------

TEST(BackoffTest, backoff_test) {

    BackOff one;
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQ(0.500, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQ(1.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQ(1.500, one.get());
    EXPECT_TRUE(one.shouldWarn());
    for (int i = 4; i < 41; i++) {
        EXPECT_EQ(0.5 * i, one.get());
    }
    for (int i = 1; i < 1000; i++) {
        EXPECT_EQ(20.0, one.get());
    }

    BackOff two;
    EXPECT_FALSE(two.shouldWarn());
    for (int i = 1; i < 50; i++) {
        double expect = 0.5 * i;
        if (expect > 20.0) expect = 20.0;
        EXPECT_EQ(expect, two.get());
        if (i == 3 || i == 8 || i == 18) {
            EXPECT_TRUE(two.shouldWarn());
        } else {
            EXPECT_FALSE(two.shouldWarn());
        }
    }
    two.reset();
    EXPECT_FALSE(two.shouldWarn());
    for (int i = 1; i < 50; i++) {
        double expect = 0.5 * i;
        if (expect > 20.0) expect = 20.0;
        EXPECT_EQ(expect, two.get());
        if (i == 3 || i == 8 || i == 18) {
            EXPECT_TRUE(two.shouldWarn());
        } else {
            EXPECT_FALSE(two.shouldWarn());
        }
    }
    for (int i = 0; i < 50000; i++) {
        EXPECT_EQ(20.0, two.get());
        if ((i % 180) == 5) {
            EXPECT_TRUE(two.shouldWarn());
        } else {
            EXPECT_FALSE(two.shouldWarn());
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
