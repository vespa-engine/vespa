// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/completion.h>
#include <vespa/vespalib/coro/waiting_for.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::coro;

struct AsyncService {
    std::vector<WaitingFor<int>> pending;
    auto get_value() {
        return wait_for<int>([&](WaitingFor<int> handle)
                             {
                                 pending.push_back(std::move(handle));
                             });
    }
};

struct AsyncVoidService {
    std::vector<void*> pending;
    auto get_value() {
        return wait_for<int>([&](WaitingFor<int> handle)
                             {
                                 pending.push_back(handle.release());
                             });
    }
};

struct SyncService {
    auto get_value() {
        return wait_for<int>([](WaitingFor<int> handle)
                                {
                                    handle.set_value(42);
                                    return handle.mu(); // symmetric transfer
                                });
    }
};

template<typename Service>
Lazy<int> wait_for_value(Service &service) {
    int value = co_await service.get_value();
    co_return value;
}

template <typename T>
Lazy<T> wait_for_fun(auto &&fun) {
    T result = co_await wait_for<T>(fun);
    co_return result;
}

TEST(WaitingForTest, wait_for_external_async_int) {
    AsyncService service;
    auto res = make_future(wait_for_value(service));
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::timeout);
    ASSERT_EQ(service.pending.size(), 1);
    service.pending[0].set_value(42);
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::timeout);
    service.pending.clear();
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::ready);
    EXPECT_EQ(res.get(), 42);
}

TEST(WaitingForTest, wait_for_external_async_int_calculated_by_coroutine) {
    AsyncService service1;
    AsyncService service2;
    auto res = make_future(wait_for_value(service1));
    ASSERT_EQ(service1.pending.size(), 1);
    {
        async_wait(wait_for_value(service2), std::move(service1.pending[0]));
        service1.pending.clear();
    }
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::timeout);
    ASSERT_EQ(service2.pending.size(), 1);
    service2.pending[0].set_value(42);
    service2.pending.clear();
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::ready);
    EXPECT_EQ(res.get(), 42);
}

TEST(WaitingForTest, wait_for_external_async_int_via_void_ptr) {
    AsyncVoidService service;
    auto res = make_future(wait_for_value(service));
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::timeout);
    ASSERT_EQ(service.pending.size(), 1);
    {
        auto handle = WaitingFor<int>::from_pointer(service.pending[0]);
        handle.set_value(42);
        EXPECT_TRUE(res.wait_for(0ms) == std::future_status::timeout);
    }
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::ready);
    EXPECT_EQ(res.get(), 42);
}

TEST(WaitingForTest, wait_for_external_sync_int) {
    SyncService service;
    auto res = make_future(wait_for_value(service));
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::ready);
    EXPECT_EQ(res.get(), 42);
}

TEST(WaitingForTest, wait_for_move_only_value) {
    auto val = std::make_unique<int>(42);
    auto fun = [&val](auto handle){ handle.set_value(std::move(val)); }; // asymmetric transfer
    auto res = make_future(wait_for_fun<decltype(val)>(fun));
    EXPECT_TRUE(res.wait_for(0ms) == std::future_status::ready);
    EXPECT_EQ(*res.get(), 42);
}

TEST(WaitingForTest, set_error) {
    PromiseState<int> state;
    WaitingFor<int> pending = WaitingFor<int>::from_state(state);
    pending.set_error(std::make_exception_ptr(13));
    EXPECT_TRUE(state.result.has_error());
}

TEST(WaitingForTest, set_done) {
    PromiseState<int> state;
    WaitingFor<int> pending = WaitingFor<int>::from_state(state);
    pending.set_value(5);
    EXPECT_TRUE(state.result.has_value());
    pending.set_done();
    EXPECT_TRUE(state.result.was_canceled());
}

GTEST_MAIN_RUN_ALL_TESTS()
