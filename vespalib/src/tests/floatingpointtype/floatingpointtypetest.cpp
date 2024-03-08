// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/objects/floatingpointtype.h>

TEST(FloatingPointTypeTest, test_floating_point)
{
    vespalib::Double d1(1.0);
    vespalib::Double d2(1.000000000000001);
    vespalib::Double d3(-1.00000000000001);
    vespalib::Double d4(4.0);

    EXPECT_TRUE(d1.getValue() != d2.getValue());

    EXPECT_EQ(d1, d2);
    EXPECT_EQ(d2, d1);

    EXPECT_NE(d1, d3);
    EXPECT_NE(d1, d4);

    EXPECT_TRUE(d1 - d2 == 0);
    EXPECT_TRUE(d2 - d1 == 0);

    EXPECT_TRUE(d1 - 1 == 0);
    EXPECT_TRUE(d1 + 1 != 0);

    EXPECT_TRUE(d2 * d4 == 4.0);
    EXPECT_TRUE(d2 / d4 == 0.25);

    EXPECT_TRUE(d1 >= 1);
    EXPECT_TRUE(d1 <= 1);
    EXPECT_TRUE(!(d1 < 1));
    EXPECT_TRUE(!(d1 > 1));

    EXPECT_EQ(d2 * 4, d4);

    EXPECT_EQ(++d4, 5.0);
    EXPECT_EQ(d4++, 5.0);
    EXPECT_EQ(d4, 6.0);

    d4 /= 3;
    EXPECT_EQ(d4, 2.00000000001);
    d4 *= 2;
    EXPECT_EQ(d4, 4.000000000001);

    EXPECT_EQ(--d4, 3.0);
    EXPECT_EQ(d4--, 3.0);
    EXPECT_EQ(d4, 2.0);
    d4 /= 0.50000000001;

    EXPECT_EQ(d4, 4.0);

    EXPECT_TRUE(!(d3 + 1 > 0));
    EXPECT_TRUE(!(d3 + 1 < 0));
}

GTEST_MAIN_RUN_ALL_TESTS()
