// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

TEST("will pass main") {
    TEST_TRACE();
    EXPECT_TRUE(true);
}

TEST("will fail main") {
    TEST_TRACE();
    EXPECT_TRUE(false);
}

TEST_MAIN() { TEST_RUN_ALL(); }
