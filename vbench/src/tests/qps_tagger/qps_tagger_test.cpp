// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST_FF("qps tagger", RequestReceptor(), QpsTagger(2.0, f1)) {
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_APPROX(0.0, f1.request->scheduledTime(), 10e-6);
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_APPROX(0.5, f1.request->scheduledTime(), 10e-6);
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_APPROX(1.0, f1.request->scheduledTime(), 10e-6);
    f2.handle(Request::UP(new Request()));
    ASSERT_TRUE(f1.request.get() != 0);
    EXPECT_APPROX(1.5, f1.request->scheduledTime(), 10e-6);
}

TEST_MAIN() { TEST_RUN_ALL(); }
