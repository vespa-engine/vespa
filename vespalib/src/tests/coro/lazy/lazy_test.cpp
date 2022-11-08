// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/completion.h>
#include <vespa/vespalib/coro/schedule.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <mutex>

#include <thread>

using vespalib::Executor;
using vespalib::Gate;
using vespalib::coro::Lazy;
using vespalib::coro::Received;
using vespalib::coro::ScheduleFailedException;
using vespalib::coro::schedule;
using vespalib::coro::sync_wait;
using vespalib::coro::try_schedule;

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
Lazy<std::pair<bool,T>> try_schedule_on(Executor &executor, Lazy<T> value) {
    std::cerr << "switching from thread " << std::this_thread::get_id() << std::endl;
    bool accepted = co_await try_schedule(executor);
    std::cerr << "........... to thread " << std::this_thread::get_id() << std::endl;
    co_return std::make_pair(accepted, co_await std::move(value));
}

template <typename T>
Lazy<T> schedule_on(Executor &executor, Lazy<T> value) {
    std::cerr << "switching from thread " << std::this_thread::get_id() << std::endl;
    co_await schedule(executor);
    std::cerr << "........... to thread " << std::this_thread::get_id() << std::endl;
    co_return co_await std::move(value);
}

TEST(LazyTest, simple_lazy_value) {
    auto lazy = make_lazy(42);
    auto result = sync_wait(std::move(lazy));
    EXPECT_EQ(result, 42);
}

TEST(LazyTest, async_sum_of_async_values) {
    auto lazy = async_add_values(10, 20);
    auto result = sync_wait(std::move(lazy));
    EXPECT_EQ(result, 30);
}

TEST(LazyTest, async_sum_of_external_async_values) {
    auto a = make_lazy(100);
    auto b = make_lazy(200);
    auto lazy = async_sum(std::move(a), std::move(b));
    auto result = sync_wait(std::move(lazy));
    EXPECT_EQ(result, 300);
}

TEST(LazyTest, extract_rvalue_from_lazy_in_coroutine) {
    auto lazy = extract_rvalue();
    auto result = sync_wait(std::move(lazy));
    EXPECT_EQ(result, 123);
}

TEST(LazyTest, extract_rvalue_from_lazy_in_sync_wait) {
    auto result = sync_wait(move_only_int());
    EXPECT_EQ(*result, 123);
}

TEST(LazyTest, calculate_result_in_another_thread) {
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    auto result = sync_wait(try_schedule_on(executor, make_lazy(7)));
    EXPECT_EQ(result.first, true);
    EXPECT_EQ(result.second, 7);
    auto result2 = sync_wait(schedule_on(executor, make_lazy(8)));
    EXPECT_EQ(result2, 8);
}

TEST(LazyTest, exceptions_are_propagated) {
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    auto lazy = try_schedule_on(executor, forward_value(will_throw()));
    EXPECT_THROW(sync_wait(std::move(lazy)), vespalib::RequireFailedException);
}

TEST(LazyTest, not_able_to_switch_thread_if_executor_is_shut_down) {
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    executor.shutdown();
    auto result = sync_wait(try_schedule_on(executor, make_lazy(7)));
    EXPECT_EQ(result.first, false);
    EXPECT_EQ(result.second, 7);
    auto lazy = schedule_on(executor, make_lazy(8));
    EXPECT_THROW(sync_wait(std::move(lazy)), ScheduleFailedException);
}

TEST(LazyTest, async_wait_with_lambda) {
    Gate gate;
    Received<int> result;
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    auto lazy = schedule_on(executor, make_lazy(7));
    async_wait(std::move(lazy), [&](auto res)
                                {
                                    result = res;
                                    gate.countDown();
                                });
    gate.await();
    EXPECT_EQ(result.get_value(), 7);
}

TEST(LazyTest, async_wait_with_error) {
    Gate gate;
    Received<int> result;
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    auto lazy = schedule_on(executor, will_throw());
    async_wait(std::move(lazy), [&](auto res)
                                {
                                    result = res;
                                    gate.countDown();
                                });
    gate.await();
    EXPECT_THROW(result.get_value(), vespalib::RequireFailedException);
}

TEST(LazyTest, async_wait_with_move_only_result) {
    Gate gate;
    Received<std::unique_ptr<int>> result;
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    auto lazy = schedule_on(executor, move_only_int());
    async_wait(std::move(lazy), [&](auto res)
                                {
                                    result = std::move(res);
                                    gate.countDown();
                                });
    gate.await();
    EXPECT_EQ(*(result.get_value()), 123);
}

struct Refs {
    Gate &gate;
    Received<std::unique_ptr<int>> &result;
    Refs(Gate &gate_in, Received<std::unique_ptr<int>> &result_in)
      : gate(gate_in), result(result_in) {}
};

TEST(LazyTest, async_wait_with_move_only_result_and_move_only_lambda) {
    Gate gate;
    Received<std::unique_ptr<int>> result;
    vespalib::ThreadStackExecutor executor(1, 128_Ki);
    auto lazy = schedule_on(executor, move_only_int());
    async_wait(std::move(lazy), [refs = std::make_unique<Refs>(gate,result)](auto res)
                                {
                                    refs->result = std::move(res);
                                    refs->gate.countDown();
                                });
    gate.await();
    EXPECT_EQ(*(result.get_value()), 123);
}

GTEST_MAIN_RUN_ALL_TESTS()
