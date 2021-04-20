// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/slobrok/backoff.h>

#include <vespa/log/log.h>
LOG_SETUP("backoff_test");

using slobrok::api::BackOff;

TEST_SETUP(Test);

//-----------------------------------------------------------------------------

static double expectWait[21] = {
	0.5, 1.0, 1.5, 2.0, 2.5,
        3.0, 3.5, 4.0, 4.5,
	5.0, 6.0, 7.0, 8.0, 9.0,
        10, 15, 20, 25, 30, 30, 30
};

int
Test::Main()
{
    TEST_INIT("backoff_test");

    BackOff one;
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(0.500, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(1.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(1.500, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(2.000, one.get());
    EXPECT_TRUE(one.shouldWarn());

    EXPECT_EQUAL(2.500, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(3.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(3.500, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(4.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(4.500, one.get());
    EXPECT_TRUE(one.shouldWarn());

    EXPECT_EQUAL(5.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(6.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(7.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(8.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(9.000, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(10.00, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(15.00, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(20.00, one.get());
    EXPECT_TRUE(one.shouldWarn());

    EXPECT_EQUAL(25.00, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(30.00, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(30.00, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(30.00, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(30.00, one.get());
    EXPECT_FALSE(one.shouldWarn());
    EXPECT_EQUAL(30.00, one.get());
    EXPECT_FALSE(one.shouldWarn());

    TEST_FLUSH();

    BackOff two;
    for (int i = 0; i < 21; i++) {
        EXPECT_EQUAL(expectWait[i], two.get());
        if (i == 3 || i == 8 || i == 16) {
            EXPECT_TRUE(two.shouldWarn());
        } else {
            EXPECT_FALSE(two.shouldWarn());
        }
    }
    two.reset();
    for (int i = 0; i < 21; i++) {
        EXPECT_EQUAL(expectWait[i], two.get());
        if (i == 7 || i == 15) {
            EXPECT_TRUE(two.shouldWarn());
        } else {
            EXPECT_FALSE(two.shouldWarn());
        }
    }
    TEST_DONE();
}
