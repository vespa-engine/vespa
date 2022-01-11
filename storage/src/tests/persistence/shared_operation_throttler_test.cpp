// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/storage/persistence/shared_operation_throttler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/barrier.h>
#include <chrono>
#include <thread>

using namespace ::testing;

namespace storage {

using ThrottleToken = SharedOperationThrottler::Token;

TEST(SharedOperationThrottlerTest, unlimited_throttler_does_not_throttle) {
    // We technically can't test that the unlimited throttler _never_ throttles, but at
    // least check that it doesn't throttle _twice_, and then induce from this ;)
    auto throttler = SharedOperationThrottler::make_unlimited_throttler();
    auto token1 = throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
    auto token2 = throttler->blocking_acquire_one();
    EXPECT_TRUE(token2.valid());
    // Window size should be zero (i.e. unlimited) for unlimited throttler
    EXPECT_EQ(throttler->current_window_size(), 0);
}

TEST(SharedOperationThrottlerTest, dynamic_throttler_respects_initial_window_size) {
    auto throttler = SharedOperationThrottler::make_dynamic_throttler(1);
    auto token1 = throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
    auto token2 = throttler->try_acquire_one();
    EXPECT_FALSE(token2.valid());

    EXPECT_EQ(throttler->current_window_size(), 1);
}

TEST(SharedOperationThrottlerTest, blocking_acquire_returns_immediately_if_slot_available) {
    auto throttler = SharedOperationThrottler::make_dynamic_throttler(1);
    auto token = throttler->blocking_acquire_one();
    EXPECT_TRUE(token.valid());
    token.reset();
    token = throttler->blocking_acquire_one(600s); // Should never block.
    EXPECT_TRUE(token.valid());
}

TEST(SharedOperationThrottlerTest, blocking_call_woken_up_if_throttle_slot_available) {
    auto throttler = SharedOperationThrottler::make_dynamic_throttler(1);
    vespalib::Barrier barrier(2);
    std::thread t([&] {
        auto token = throttler->try_acquire_one();
        assert(token.valid());
        barrier.await();
        while (throttler->waiting_threads() != 1) {
            std::this_thread::sleep_for(100us);
        }
        // Implicit token release at thread scope exit
    });
    barrier.await();
    auto token = throttler->blocking_acquire_one();
    EXPECT_TRUE(token.valid());
    t.join();
}

TEST(SharedOperationThrottlerTest, time_bounded_blocking_acquire_waits_for_timeout) {
    auto throttler = SharedOperationThrottler::make_dynamic_throttler(1);
    auto window_filling_token = throttler->try_acquire_one();
    auto before = std::chrono::steady_clock::now();
    // Will block for at least 1ms. Since no window slot will be available by that time,
    // an invalid token should be returned.
    auto token = throttler->blocking_acquire_one(1ms);
    auto after = std::chrono::steady_clock::now();
    EXPECT_TRUE((after - before) >= 1ms);
    EXPECT_FALSE(token.valid());
}

TEST(SharedOperationThrottlerTest, default_constructed_token_is_invalid) {
    ThrottleToken token;
    EXPECT_FALSE(token.valid());
    token.reset(); // no-op
    EXPECT_FALSE(token.valid());
}

TEST(SharedOperationThrottlerTest, token_destruction_frees_up_throttle_window_slot) {
    auto throttler = SharedOperationThrottler::make_dynamic_throttler(1);
    {
        auto token = throttler->try_acquire_one();
        EXPECT_TRUE(token.valid());
    }
    auto token = throttler->try_acquire_one();
    EXPECT_TRUE(token.valid());
}

TEST(SharedOperationThrottlerTest, token_can_be_moved_and_reset) {
    auto throttler = SharedOperationThrottler::make_dynamic_throttler(1);
    auto token1 = throttler->try_acquire_one();
    auto token2 = std::move(token1); // move ctor
    EXPECT_TRUE(token2.valid());
    EXPECT_FALSE(token1.valid());
    ThrottleToken token3;
    token3 = std::move(token2); // move assignment op
    EXPECT_TRUE(token3.valid());
    EXPECT_FALSE(token2.valid());

    // Trying to fetch new token should not succeed due to active token and win size of 1
    token1 = throttler->try_acquire_one();
    EXPECT_FALSE(token1.valid());
    // Resetting the token should free up the slot in the window
    token3.reset();
    token1 = throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
}

// TODO ideally we'd test that the dynamic throttler has a window size that is actually
//  dynamic, but the backing DynamicThrottlePolicy implementation is a black box so
//  it's not trivial to know how to do this reliably.

}
