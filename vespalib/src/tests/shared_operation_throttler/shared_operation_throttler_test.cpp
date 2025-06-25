// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/shared_operation_throttler.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>
#include <cassert>

using vespalib::steady_clock;

using namespace ::testing;

namespace vespalib {

using ThrottleToken = SharedOperationThrottler::Token;

struct DynamicThrottleFixture {
    std::unique_ptr<SharedOperationThrottler> _throttler;

    DynamicThrottleFixture() {
        SharedOperationThrottler::DynamicThrottleParams params;
        params.window_size_increment = 1;
        params.min_window_size = 1;
        // By default, tests will not set a resource limit, which means they operate
        // as-if the resource limit does not exist.
        _throttler = SharedOperationThrottler::make_dynamic_throttler(params);
    }
};

struct SharedOperationHandlerTest : Test {
    DynamicThrottleFixture f;

    static SharedOperationThrottler::DynamicThrottleParams params_with_resource_limit(uint64_t limit) {
        SharedOperationThrottler::DynamicThrottleParams params;
        params.window_size_increment = 1;
        params.min_window_size = 10; // Enough that tests aren't throttled by the throttle policy itself.
        params.resource_usage_soft_limit = limit;
        return params;
    }
};

TEST_F(SharedOperationHandlerTest, unlimited_throttler_does_not_throttle) {
    // We technically can't test that the unlimited throttler _never_ throttles, but at
    // least check that it doesn't throttle _twice_, and then induce from this ;)
    auto throttler = SharedOperationThrottler::make_unlimited_throttler();
    auto token1 = throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
    auto token2 = throttler->blocking_acquire_one();
    EXPECT_TRUE(token2.valid());
    // Window size should be zero (i.e. unlimited) for unlimited throttler
    EXPECT_EQ(throttler->current_window_size(), 0u);
    // But we still track the active token count
    EXPECT_EQ(throttler->current_active_token_count(), 2u);
}

TEST_F(SharedOperationHandlerTest, unlimited_throttler_tracks_max_resource_usage) {
    auto throttler = SharedOperationThrottler::make_unlimited_throttler();
    EXPECT_EQ(throttler->max_resource_usage(), 0);
    auto token1 = throttler->try_acquire_one(1000);
    ASSERT_TRUE(token1.valid());
    EXPECT_EQ(throttler->max_resource_usage(), 1000);
    auto token2 = throttler->try_acquire_one(2000);
    ASSERT_TRUE(token2.valid());
    EXPECT_EQ(throttler->max_resource_usage(), 3000);
    token2.reset();
    EXPECT_EQ(throttler->max_resource_usage(), 3000);
    auto token3 = throttler->try_acquire_one(1900);
    ASSERT_TRUE(token3.valid());
    EXPECT_EQ(throttler->max_resource_usage(), 3000); // Monotonically increases
    auto token4 = throttler->try_acquire_one(101);
    ASSERT_TRUE(token4.valid());
    EXPECT_EQ(throttler->max_resource_usage(), 3001);
}

TEST_F(SharedOperationHandlerTest, dynamic_throttler_respects_initial_window_size) {
    auto token1 = f._throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
    auto token2 = f._throttler->try_acquire_one();
    EXPECT_FALSE(token2.valid());

    EXPECT_EQ(f._throttler->current_window_size(), 1u);
    EXPECT_EQ(f._throttler->current_active_token_count(), 1u);
}

TEST_F(SharedOperationHandlerTest, blocking_acquire_returns_immediately_if_slot_available) {
    auto token = f._throttler->blocking_acquire_one();
    EXPECT_TRUE(token.valid());
    token.reset();
    token = f._throttler->blocking_acquire_one(steady_clock::now() + 600s); // Should never block.
    EXPECT_TRUE(token.valid());
}

TEST_F(SharedOperationHandlerTest, blocking_call_woken_up_if_throttle_slot_available) {
    vespalib::Barrier barrier(2);
    std::thread t([&] {
        auto token = f._throttler->try_acquire_one();
        assert(token.valid());
        barrier.await();
        while (f._throttler->waiting_threads() != 1) {
            std::this_thread::sleep_for(100us);
        }
        // Implicit token release at thread scope exit
    });
    barrier.await();
    auto token = f._throttler->blocking_acquire_one();
    EXPECT_TRUE(token.valid());
    t.join();
}

TEST_F(SharedOperationHandlerTest, time_bounded_blocking_acquire_waits_for_timeout) {
    auto window_filling_token = f._throttler->try_acquire_one();
    auto before = steady_clock::now();
    // Will block for at least 1ms. Since no window slot will be available by that time,
    // an invalid token should be returned.
    auto token = f._throttler->blocking_acquire_one(before + 1ms);
    auto after = steady_clock::now();
    EXPECT_TRUE((after - before) >= 1ms);
    EXPECT_FALSE(token.valid());
}

TEST_F(SharedOperationHandlerTest, default_constructed_token_is_invalid) {
    ThrottleToken token;
    EXPECT_FALSE(token.valid());
    token.reset(); // no-op
    EXPECT_FALSE(token.valid());
}

TEST_F(SharedOperationHandlerTest, token_destruction_frees_up_throttle_window_slot) {
    {
        auto token = f._throttler->try_acquire_one();
        EXPECT_TRUE(token.valid());
        EXPECT_EQ(f._throttler->current_active_token_count(), 1u);
    }
    EXPECT_EQ(f._throttler->current_active_token_count(), 0u);

    auto token = f._throttler->try_acquire_one();
    EXPECT_TRUE(token.valid());
    EXPECT_EQ(f._throttler->current_active_token_count(), 1u);
}

TEST_F(SharedOperationHandlerTest, token_can_be_moved_and_reset) {
    auto token1 = f._throttler->try_acquire_one();
    auto token2 = std::move(token1); // move ctor
    EXPECT_TRUE(token2.valid());
    EXPECT_FALSE(token1.valid());
    ThrottleToken token3;
    token3 = std::move(token2); // move assignment op
    EXPECT_TRUE(token3.valid());
    EXPECT_FALSE(token2.valid());

    // Trying to fetch new token should not succeed due to active token and win size of 1
    token1 = f._throttler->try_acquire_one();
    EXPECT_FALSE(token1.valid());
    // Resetting the token should free up the slot in the window
    token3.reset();
    token1 = f._throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
}

TEST_F(SharedOperationHandlerTest, resource_soft_limit_takes_precedence_over_window_size) {
    f._throttler = SharedOperationThrottler::make_dynamic_throttler(params_with_resource_limit(3000));
    ASSERT_EQ(f._throttler->current_window_size(), 10);
    auto token1 = f._throttler->try_acquire_one(2000);
    ASSERT_TRUE(token1.valid());
    EXPECT_EQ(f._throttler->current_resource_usage(), 2000);
    auto token2 = f._throttler->try_acquire_one(1001); // Would go past limit, even though window has room
    ASSERT_FALSE(token2.valid());
    EXPECT_EQ(f._throttler->current_resource_usage(), 2000);
    auto token3 = f._throttler->try_acquire_one(1000); // Goldilocks fit, just right
    EXPECT_TRUE(token3.valid());
    EXPECT_EQ(f._throttler->current_resource_usage(), 3000);
}

TEST_F(SharedOperationHandlerTest, resource_soft_limit_allows_single_op_even_if_it_exceeds_limit) {
    f._throttler = SharedOperationThrottler::make_dynamic_throttler(params_with_resource_limit(3000));
    // Should be allowed even if it exceeds the limit since we always need to allow at
    // least one operation to ensure liveness.
    auto token1 = f._throttler->try_acquire_one(5000);
    ASSERT_TRUE(token1.valid());
    EXPECT_EQ(f._throttler->current_resource_usage(), 5000);
    auto token2 = f._throttler->try_acquire_one(1);
    ASSERT_FALSE(token2.valid());
    EXPECT_EQ(f._throttler->current_resource_usage(), 5000);
}

TEST_F(SharedOperationHandlerTest, token_destruction_frees_up_resource_usage_of_token) {
    f._throttler = SharedOperationThrottler::make_dynamic_throttler(params_with_resource_limit(10000));
    auto token1 = f._throttler->try_acquire_one(5000);
    ASSERT_TRUE(token1.valid());
    auto token2 = f._throttler->try_acquire_one(3000);
    ASSERT_TRUE(token2.valid());
    EXPECT_EQ(f._throttler->current_resource_usage(), 8000);
    token1.reset();
    EXPECT_EQ(f._throttler->current_resource_usage(), 3000);
    auto token2_moved = std::move(token2); // Must be tracked across moves
    token2_moved.reset();
    EXPECT_EQ(f._throttler->current_resource_usage(), 0);
}

TEST_F(SharedOperationHandlerTest, resource_usage_overflow_fails_token_acquisition) {
    f._throttler = SharedOperationThrottler::make_dynamic_throttler(params_with_resource_limit(3000));
    auto token1 = f._throttler->try_acquire_one(1000);
    ASSERT_TRUE(token1.valid());
    auto token2 = f._throttler->try_acquire_one(UINT64_MAX - 999);
    EXPECT_FALSE(token2.valid());
}

TEST_F(SharedOperationHandlerTest, unlimited_resource_usage_does_not_block_token_acquisition) {
    f._throttler = SharedOperationThrottler::make_dynamic_throttler(params_with_resource_limit(0)); // 0 == inf
    auto token1 = f._throttler->try_acquire_one(10'000);
    EXPECT_TRUE(token1.valid());
    auto token2 = f._throttler->try_acquire_one(20'000);
    EXPECT_TRUE(token2.valid());
    // We still track the resource usage
    EXPECT_EQ(f._throttler->current_resource_usage(), 30'000);
}

TEST_F(SharedOperationHandlerTest, dynamic_operation_throttler_tracks_max_resource_usage) {
    f._throttler = SharedOperationThrottler::make_dynamic_throttler(params_with_resource_limit(0)); // 0 == inf
    EXPECT_EQ(f._throttler->max_resource_usage(), 0);
    auto token1 = f._throttler->try_acquire_one(1000);
    ASSERT_TRUE(token1.valid());
    EXPECT_EQ(f._throttler->max_resource_usage(), 1000);
    auto token2 = f._throttler->try_acquire_one(2000);
    ASSERT_TRUE(token2.valid());
    EXPECT_EQ(f._throttler->max_resource_usage(), 3000);
    token2.reset();
    EXPECT_EQ(f._throttler->max_resource_usage(), 3000);
    auto token3 = f._throttler->try_acquire_one(1900);
    ASSERT_TRUE(token3.valid());
    EXPECT_EQ(f._throttler->max_resource_usage(), 3000); // Monotonically increases
    auto token4 = f._throttler->try_acquire_one(101);
    ASSERT_TRUE(token4.valid());
    EXPECT_EQ(f._throttler->max_resource_usage(), 3001);
}

// Note on test semantics: these tests are adapted from a subset of the MessageBus
// throttling tests. Some tests have been simplified due to no longer having access
// to the low-level DynamicThrottlePolicy API.

struct WindowFixture {
    uint64_t _milli_time;
    std::unique_ptr<SharedOperationThrottler> _throttler;

    WindowFixture(uint32_t window_size_increment = 5,
                  uint32_t min_window_size = 20,
                  uint32_t max_window_size = INT_MAX)
        : _milli_time(0),
          _throttler()
    {
        SharedOperationThrottler::DynamicThrottleParams params;
        params.resize_rate = 1;
        params.window_size_increment = window_size_increment;
        params.min_window_size = min_window_size;
        params.max_window_size = max_window_size;
        params.window_size_decrement_factor = 2;
        params.window_size_backoff = 0.9;
        _throttler = SharedOperationThrottler::make_dynamic_throttler(params, [&]() noexcept {
            return steady_time(std::chrono::milliseconds(_milli_time));
        });
    }

    std::vector<SharedOperationThrottler::Token> fill_entire_throttle_window() {
        std::vector<SharedOperationThrottler::Token> tokens;
        while (true) {
            auto token = _throttler->try_acquire_one();
            if (!token.valid()) {
                break;
            }
            tokens.emplace_back(std::move(token));
        }
        return tokens;
    }

    uint32_t attempt_converge_on_stable_window_size(uint32_t max_pending) {
        for (uint32_t i = 0; i < 999; ++i) {
            auto tokens = fill_entire_throttle_window();
            uint32_t num_pending = static_cast<uint32_t>(tokens.size());

            uint64_t trip_time = (num_pending < max_pending) ? 1000 : 1000 + (num_pending - max_pending) * 1000;
            _milli_time += trip_time;
            // Throttle window slots implicitly freed up as tokens are destructed.
        }
        uint32_t ret = _throttler->current_window_size();
        fprintf(stderr, "attempt_converge_on_stable_window_size() = %u\n", ret);
        return ret;
    }
};

TEST(WindowedSharedOperationThrottlerTest, window_size_changes_dynamically_based_on_throughput) {
    WindowFixture f;
    uint32_t window_size = f.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 90 && window_size <= 105);

    window_size = f.attempt_converge_on_stable_window_size(200);
    ASSERT_TRUE(window_size >= 180 && window_size <= 205);

    window_size = f.attempt_converge_on_stable_window_size(50);
    ASSERT_TRUE(window_size >= 45 && window_size <= 55);

    window_size = f.attempt_converge_on_stable_window_size(500);
    ASSERT_TRUE(window_size >= 450 && window_size <= 505);

    window_size = f.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 90 && window_size <= 115);
}

TEST(WindowedSharedOperationThrottlerTest, window_size_is_reset_after_idle_time_period) {
    WindowFixture f(5, 1);
    double window_size = f.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 90 && window_size <= 110);

    f._milli_time += 30001; // Not yet past 60s idle time
    auto tokens = f.fill_entire_throttle_window();
    ASSERT_TRUE(tokens.size() >= 90 && tokens.size() <= 110);
    tokens.clear();

    f._milli_time += 60001; // Idle time passed
    tokens = f.fill_entire_throttle_window();
    EXPECT_EQ(tokens.size(), 1u); // Reduced to minimum window size
}

TEST(WindowedSharedOperationThrottlerTest, minimum_window_size_is_respected) {
    WindowFixture f(5, 150, INT_MAX);
    double window_size = f.attempt_converge_on_stable_window_size(200);
    ASSERT_TRUE(window_size >= 150 && window_size <= 210);
}

TEST(WindowedSharedOperationThrottlerTest, maximum_window_size_is_respected) {
    WindowFixture f(5, 1, 50);
    double window_size = f.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 40 && window_size <= 50);
}

TEST(WindowedSharedOperationThrottlerTest, zero_sized_acquire_time_delta_does_not_modify_window_size) {
    WindowFixture f(1, 1, 2);
    for (int i = 0; i < 3; ++i) {
        auto token = f._throttler->try_acquire_one();
        ASSERT_TRUE(token.valid());
        EXPECT_EQ(f._throttler->current_window_size(), 1);
        // No mock timer bump between iterations.
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
