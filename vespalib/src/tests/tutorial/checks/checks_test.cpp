// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <stdexcept>

void willThrow() {
    throw std::runtime_error("This failed");
}

TEST("require that checks work") {
    EXPECT_TRUE(true);
    EXPECT_FALSE(false);
    EXPECT_EQUAL(3, 3);
    EXPECT_NOT_EQUAL(3, 4);
    EXPECT_APPROX(3.0, 3.1, 0.2);
    EXPECT_NOT_APPROX(3.0, 3.5, 0.2);
    EXPECT_LESS(3, 4);
    EXPECT_LESS_EQUAL(3, 3);
    EXPECT_GREATER(4, 3);
    EXPECT_GREATER_EQUAL(4, 4);
    EXPECT_EXCEPTION(willThrow(), std::runtime_error, "fail");
}

TEST("this test will fail") {
    EXPECT_EQUAL(3, 4);
}

TEST_MAIN() { TEST_RUN_ALL(); }
