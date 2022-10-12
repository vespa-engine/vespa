// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/sync_wait.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <thread>

using vespalib::coro::Lazy;
using vespalib::coro::sync_wait;

std::vector<std::thread> threads;
struct JoinThreads {
    ~JoinThreads() {
        for (auto &thread: threads) {
            thread.join();
        }
        threads.clear();
    }
};

auto run_in_other_thread() {
    struct awaiter {
        bool await_ready() const noexcept { return false; }
        void await_suspend(std::coroutine_handle<> handle) const {
            threads.push_back(std::thread(handle));
        }
        void await_resume() const noexcept {}
    };
    return awaiter();
}

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

Lazy<std::unique_ptr<int>> move_only_int() {
    co_return std::make_unique<int>(123);
}

Lazy<int> extract_rvalue() {
    auto res = co_await move_only_int();
    co_return *res;
}

Lazy<int> will_throw() {
    REQUIRE_FAILED("failed on purpose");
    co_return 123;
}

template<typename T>
Lazy<T> forward_value(Lazy<T> value) {
    co_return co_await std::move(value);
}

template <typename T>
Lazy<T> switch_thread(Lazy<T> value) {
    std::cerr << "switching from thread " << std::this_thread::get_id() << std::endl;
    co_await run_in_other_thread();
    std::cerr << "........... to thread " << std::this_thread::get_id() << std::endl;
    co_return co_await value;
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

TEST(LazyTest, extract_rvalue_from_lazy_in_coroutine) {
    auto lazy = extract_rvalue();
    auto result = sync_wait(lazy);
    EXPECT_EQ(result, 123);
}

TEST(LazyTest, extract_rvalue_from_lazy_in_sync_wait) {
    auto result = sync_wait(move_only_int());
    EXPECT_EQ(*result, 123);
}

TEST(LazyTest, calculate_result_in_another_thread) {
    JoinThreads thread_guard;
    auto result = sync_wait(switch_thread(make_lazy(7)));
    EXPECT_EQ(result, 7);
}

TEST(LazyTest, exceptions_are_propagated) {
    JoinThreads thread_guard;
    auto lazy = switch_thread(forward_value(will_throw()));
    EXPECT_THROW(sync_wait(lazy), vespalib::RequireFailedException);
}

GTEST_MAIN_RUN_ALL_TESTS()
