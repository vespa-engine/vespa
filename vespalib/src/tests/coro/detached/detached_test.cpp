// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/detached.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::coro::Detached;

Detached set_result(int &res, int value) {
    res = value;
    co_return;
}

TEST(DetachedTest, call_detached_coroutine) {
    int result = 0;
    set_result(result, 42);
    EXPECT_EQ(result, 42);
}

GTEST_MAIN_RUN_ALL_TESTS()
