// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(QpsTaggerTest, qps_tagger) {
    RequestReceptor f1;
    QpsTagger f2(2.0, f1);
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request);
    EXPECT_NEAR(0.0, f1.request->scheduledTime(), 10e-6);
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request);
    EXPECT_NEAR(0.5, f1.request->scheduledTime(), 10e-6);
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request);
    EXPECT_NEAR(1.0, f1.request->scheduledTime(), 10e-6);
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request);
    EXPECT_NEAR(1.5, f1.request->scheduledTime(), 10e-6);
}

GTEST_MAIN_RUN_ALL_TESTS()
