// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("true_test");
#include <vespa/vespalib/testkit/testapp.h>

TEST_SETUP(Test)

int
Test::Main()
{
    TEST_INIT("true_test");
    EXPECT_TRUE(true);
    TEST_DONE();
}
