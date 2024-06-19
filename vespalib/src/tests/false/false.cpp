// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("false_test");
#include <vespa/vespalib/testkit/test_kit.h>

TEST("false_test") {
    EXPECT_TRUE(false);
}

TEST_MAIN() { TEST_RUN_ALL(); }
