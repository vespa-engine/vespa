// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;

void testInner() {
    EXPECT_TRUE(false);
}

void testSomething() {
    EXPECT_TRUE(false);
    TEST_DO(testInner());
    EXPECT_TRUE(false);
}

void testSomethingElse() {
    EXPECT_TRUE(false);
    TEST_DO(testInner());
    EXPECT_TRUE(false);
}

void testState() {
    EXPECT_TRUE(false);
    {
        TEST_STATE("foo");
        EXPECT_TRUE(false);
        {
            TEST_STATE("bar");
            EXPECT_TRUE(false);
            {
                TEST_STATE("baz");
                EXPECT_TRUE(false);
            }
            EXPECT_TRUE(false);
        }
        EXPECT_TRUE(false);
    }
    EXPECT_TRUE(false);
    EXPECT_TRUE(false);
    {
        TEST_DO(testSomething());
    }
    {
        TEST_STATE("something else");
        EXPECT_TRUE(false);
        TEST_DO(testSomethingElse());
        EXPECT_TRUE(false);
    }
}

TEST_MAIN() {
    testState();
}
