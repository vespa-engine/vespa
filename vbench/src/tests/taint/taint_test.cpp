// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(TaintTest, untainted) {
    Taint t;
    bool fail = t;
    EXPECT_FALSE(fail);
    EXPECT_FALSE(t.taint());
    EXPECT_EQ("", t.reason());
}

TEST(TaintTest, test_Taintable__nil) {
    const Taint &t = Taintable::nil().tainted();
    bool fail = t;
    EXPECT_FALSE(fail);
    EXPECT_FALSE(t.taint());
    EXPECT_EQ("", t.reason());
}

TEST(TaintTest, tainted) {
    Taint t("argh");
    bool fail = t;
    EXPECT_TRUE(fail);
    EXPECT_TRUE(t.taint());
    EXPECT_EQ("argh", t.reason());
}

TEST(TaintTest, reset_taint) {
    Taint t;
    EXPECT_FALSE(t.taint());
    EXPECT_EQ("", t.reason());
    t.reset("argh");
    EXPECT_TRUE(t.taint());
    EXPECT_EQ("argh", t.reason());
    t.reset();
    EXPECT_FALSE(t.taint());
    EXPECT_EQ("", t.reason());
}

GTEST_MAIN_RUN_ALL_TESTS()
