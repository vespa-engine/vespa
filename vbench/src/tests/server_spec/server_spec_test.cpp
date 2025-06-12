// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(ServerSpecTest, empty) {
    ServerSpec spec;
    EXPECT_EQ("", spec.host);
    EXPECT_EQ(0, spec.port);
}

TEST(ServerSpecTest, standard) {
    ServerSpec spec("foo", 3);
    EXPECT_EQ("foo", spec.host);
    EXPECT_EQ(3, spec.port);
}

TEST(ServerSpecTest, compare) {
    ServerSpec spec0("foo", 3);
    ServerSpec spec1("foo", 3);
    ServerSpec spec2("bar", 3);
    ServerSpec spec3("foo", 4);
    ServerSpec spec4("bar", 4);
    EXPECT_TRUE(spec0 == spec1);
    EXPECT_TRUE(spec1 == spec0);
    EXPECT_FALSE(spec0 == spec2);
    EXPECT_FALSE(spec2 == spec0);
    EXPECT_TRUE(spec2 < spec0);
    EXPECT_TRUE(spec0 < spec3);
    EXPECT_TRUE(spec0 < spec4);
    EXPECT_FALSE(spec0 < spec1);
    EXPECT_FALSE(spec0 < spec2);
    EXPECT_FALSE(spec3 < spec0);
    EXPECT_FALSE(spec4 < spec0);
}

GTEST_MAIN_RUN_ALL_TESTS()
