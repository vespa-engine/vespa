// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST_FF("generate request", RequestReceptor(), RequestGenerator(TEST_PATH("input.txt"), f1)) {
    f2.run();
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_EQUAL("/this/is/url", f1.request->url());
    EXPECT_FALSE(f2.tainted());
}

TEST_FF("input not found", RequestReceptor(), RequestGenerator("no_such_input.txt", f1)) {
    f2.run();
    EXPECT_TRUE(f1.request.get() == 0);
    EXPECT_TRUE(f2.tainted());
}

TEST_FF("abort request generation", RequestReceptor(), RequestGenerator(TEST_PATH("input.txt"), f1)) {
    f2.abort();
    f2.run();
    EXPECT_TRUE(f1.request.get() == 0);
    EXPECT_FALSE(f2.tainted());
}

TEST_MAIN() { TEST_RUN_ALL(); }
