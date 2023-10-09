// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

TEST("require something") {
    EXPECT_TRUE(true);
}

TEST("require something else") {
    EXPECT_TRUE(true);
}

TEST_MAIN() { TEST_RUN_ALL(); }
