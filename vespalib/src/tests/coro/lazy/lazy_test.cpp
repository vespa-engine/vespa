// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/sync_wait.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::coro::Lazy;
using vespalib::coro::sync_wait;

Lazy<int> make_lazy(int value) {
    co_return value;
}

Lazy<int> async_add_values(int a, int b) {
    auto lazy_a = make_lazy(a);
    auto lazy_b = make_lazy(b);
    co_return (co_await lazy_a + co_await lazy_b);
}

Lazy<int> async_sum(Lazy<int> a, Lazy<int> b) {
    co_return (co_await a + co_await b);
}

TEST(LazyTest, simple_lazy_value) {
    auto lazy = make_lazy(42);
    auto result = sync_wait(lazy);
    EXPECT_EQ(result, 42);
}

TEST(LazyTest, async_sum_of_async_values) {
    auto lazy = async_add_values(10, 20);
    auto result = sync_wait(lazy);
    EXPECT_EQ(result, 30);
}

TEST(LazyTest, async_sum_of_external_async_values) {
    auto a = make_lazy(100);
    auto b = make_lazy(200);
    auto lazy = async_sum(std::move(a), std::move(b));
    auto result = sync_wait(lazy);
    EXPECT_EQ(result, 300);
}

GTEST_MAIN_RUN_ALL_TESTS()
