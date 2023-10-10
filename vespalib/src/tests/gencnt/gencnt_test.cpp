// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("gencnt_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/gencnt.h>

using vespalib::GenCnt;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("gencnt_test");

    GenCnt first;

    GenCnt a;
    GenCnt b;
    GenCnt c;

    a.setFromInt(5);
    b.setFromInt(5);
    c.setFromInt(5);
    EXPECT_TRUE(a == b);
    EXPECT_TRUE(!(a != b));
    EXPECT_TRUE(b.inRangeInclusive(a, c));
    EXPECT_TRUE(b.inRangeInclusive(c, a));

    a.setFromInt(5);
    b.setFromInt(6);
    c.setFromInt(7);
    EXPECT_TRUE(a != b);
    EXPECT_TRUE(!(a == b));
    EXPECT_TRUE(b.inRangeInclusive(a, c));
    EXPECT_TRUE(!b.inRangeInclusive(c, a));
    EXPECT_TRUE(!a.inRangeInclusive(b, c));
    EXPECT_TRUE(a.inRangeInclusive(c, b));
    EXPECT_TRUE(!first.inRangeInclusive(a, c));
    EXPECT_TRUE(!first.inRangeInclusive(c, a));

    a.setFromInt(10);
    c = b = a;
    b.add(10);
    c.add(20);
    EXPECT_TRUE(b.inRangeInclusive(a, c));
    EXPECT_TRUE(!b.inRangeInclusive(c, a));
    EXPECT_TRUE(!a.inRangeInclusive(b, c));
    EXPECT_TRUE(a.inRangeInclusive(c, b));
    EXPECT_TRUE(a.distance(b) == 10);
    EXPECT_TRUE(a.distance(c) == 20);
    EXPECT_TRUE(b.distance(c) == 10);
    EXPECT_TRUE(!first.inRangeInclusive(a, c));
    EXPECT_TRUE(!first.inRangeInclusive(c, a));

    a.setFromInt((uint32_t)-5);
    c = b = a;
    b.add(10);
    c.add(20);
    EXPECT_TRUE(b.inRangeInclusive(a, c));
    EXPECT_TRUE(!b.inRangeInclusive(c, a));
    EXPECT_TRUE(!a.inRangeInclusive(b, c));
    EXPECT_TRUE(a.inRangeInclusive(c, b));
    EXPECT_TRUE(a.distance(b) == 10);
    EXPECT_TRUE(a.distance(c) == 20);
    EXPECT_TRUE(b.distance(c) == 10);
    EXPECT_TRUE(!first.inRangeInclusive(a, c));
    EXPECT_TRUE(!first.inRangeInclusive(c, a));

    a.setFromInt((uint32_t)-15);
    c = b = a;
    b.add(10);
    c.add(20);
    EXPECT_TRUE(b.inRangeInclusive(a, c));
    EXPECT_TRUE(!b.inRangeInclusive(c, a));
    EXPECT_TRUE(!a.inRangeInclusive(b, c));
    EXPECT_TRUE(a.inRangeInclusive(c, b));
    EXPECT_TRUE(a.distance(b) == 10);
    EXPECT_TRUE(a.distance(c) == 20);
    EXPECT_TRUE(b.distance(c) == 10);
    EXPECT_TRUE(!first.inRangeInclusive(a, c));
    EXPECT_TRUE(!first.inRangeInclusive(c, a));

    TEST_DONE();
}
