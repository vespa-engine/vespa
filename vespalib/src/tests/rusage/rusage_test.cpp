// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/rusage.h>

using namespace vespalib;

TEST(RUsageTest, testRUsage)
{
    RUsage r1;
    EXPECT_EQ("", r1.toString());
    RUsage r2;
    EXPECT_EQ(r2.toString(), r1.toString());
    RUsage diff = r2-r1;
    EXPECT_EQ(diff.toString(), r2.toString());
    {
        RUsage then = RUsage::createSelf(steady_time(7ns));
        RUsage now = RUsage::createSelf();
        EXPECT_NE(now.toString(), then.toString());
    }
    {
        RUsage then = RUsage::createChildren(steady_time(1337583ns));
        RUsage now = RUsage::createChildren();
        EXPECT_NE(now.toString(), then.toString());
    }
    {
        timeval a, b, c, d, r;
        a.tv_usec = 7;
        a.tv_sec = 7;
        b.tv_usec = 7;
        b.tv_sec = 7;
        c.tv_usec = 1;
        c.tv_sec = 8;
        d.tv_usec = 9;
        d.tv_sec = 4;
        r = a - b;
        EXPECT_EQ(0, r.tv_sec);
        EXPECT_EQ(0, r.tv_usec);
        r = b - a;
        EXPECT_EQ(0, r.tv_sec);
        EXPECT_EQ(0, r.tv_usec);
        r = a - c;
        EXPECT_EQ(-1, r.tv_sec);
        EXPECT_EQ( 6, r.tv_usec);
        r = c - a;
        EXPECT_EQ(0, r.tv_sec);
        EXPECT_EQ(999994, r.tv_usec);
        r = a - d;
        EXPECT_EQ(2, r.tv_sec);
        EXPECT_EQ(999998, r.tv_usec);
        r = d - a;
        EXPECT_EQ(-3, r.tv_sec);
        EXPECT_EQ( 2, r.tv_usec);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
