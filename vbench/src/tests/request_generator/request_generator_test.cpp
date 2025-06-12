// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(RequestGeneratorTest, generate_request) {
    RequestReceptor f1;
    RequestGenerator f2(TEST_PATH("input.txt"), f1);
    f2.run();
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_EQ("/this/is/url", f1.request->url());
    EXPECT_FALSE(f2.tainted());
}

TEST(RequestGeneratorTest, input_not_found) {
    RequestReceptor f1;
    RequestGenerator f2("no_such_input.txt", f1);
    f2.run();
    EXPECT_TRUE(f1.request.get() == 0);
    EXPECT_TRUE(f2.tainted());
}

TEST(RequestGeneratorTest, abort_request_generation) {
    RequestReceptor f1;
    RequestGenerator f2(TEST_PATH("input.txt"), f1);
    f2.abort();
    f2.run();
    EXPECT_TRUE(f1.request.get() == 0);
    EXPECT_FALSE(f2.tainted());
}

GTEST_MAIN_RUN_ALL_TESTS()
