// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/shared_operation_throttler.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/barrier.h>
#include <thread>

using vespalib::steady_clock;

namespace vespalib {

using ThrottleToken = SharedOperationThrottler::Token;

struct DynamicThrottleFixture {
    std::unique_ptr<SharedOperationThrottler> _throttler;

    DynamicThrottleFixture() {
        SharedOperationThrottler::DynamicThrottleParams params;
        params.window_size_increment = 1;
        params.min_window_size = 1;
        _throttler = SharedOperationThrottler::make_dynamic_throttler(params);
    }
};

TEST("unlimited throttler does not throttle") {
    // We technically can't test that the unlimited throttler _never_ throttles, but at
    // least check that it doesn't throttle _twice_, and then induce from this ;)
    auto throttler = SharedOperationThrottler::make_unlimited_throttler();
    auto token1 = throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
    auto token2 = throttler->blocking_acquire_one();
    EXPECT_TRUE(token2.valid());
    // Window size should be zero (i.e. unlimited) for unlimited throttler
    EXPECT_EQUAL(throttler->current_window_size(), 0u);
    // But we still track the active token count
    EXPECT_EQUAL(throttler->current_active_token_count(), 2u);
}

TEST_F("dynamic throttler respects initial window size", DynamicThrottleFixture()) {
    auto token1 = f1._throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
    auto token2 = f1._throttler->try_acquire_one();
    EXPECT_FALSE(token2.valid());

    EXPECT_EQUAL(f1._throttler->current_window_size(), 1u);
    EXPECT_EQUAL(f1._throttler->current_active_token_count(), 1u);
}

TEST_F("blocking acquire returns immediately if slot available", DynamicThrottleFixture()) {
    auto token = f1._throttler->blocking_acquire_one();
    EXPECT_TRUE(token.valid());
    token.reset();
    token = f1._throttler->blocking_acquire_one(steady_clock::now() + 600s); // Should never block.
    EXPECT_TRUE(token.valid());
}

TEST_F("blocking call woken up if throttle slot available", DynamicThrottleFixture()) {
    vespalib::Barrier barrier(2);
    std::thread t([&] {
        auto token = f1._throttler->try_acquire_one();
        assert(token.valid());
        barrier.await();
        while (f1._throttler->waiting_threads() != 1) {
            std::this_thread::sleep_for(100us);
        }
        // Implicit token release at thread scope exit
    });
    barrier.await();
    auto token = f1._throttler->blocking_acquire_one();
    EXPECT_TRUE(token.valid());
    t.join();
}

TEST_F("time-bounded blocking acquire waits for timeout", DynamicThrottleFixture()) {
    auto window_filling_token = f1._throttler->try_acquire_one();
    auto before = steady_clock::now();
    // Will block for at least 1ms. Since no window slot will be available by that time,
    // an invalid token should be returned.
    auto token = f1._throttler->blocking_acquire_one(before + 1ms);
    auto after = steady_clock::now();
    EXPECT_TRUE((after - before) >= 1ms);
    EXPECT_FALSE(token.valid());
}

TEST("default constructed token is invalid") {
    ThrottleToken token;
    EXPECT_FALSE(token.valid());
    token.reset(); // no-op
    EXPECT_FALSE(token.valid());
}

TEST_F("token destruction frees up throttle window slot", DynamicThrottleFixture()) {
    {
        auto token = f1._throttler->try_acquire_one();
        EXPECT_TRUE(token.valid());
        EXPECT_EQUAL(f1._throttler->current_active_token_count(), 1u);
    }
    EXPECT_EQUAL(f1._throttler->current_active_token_count(), 0u);

    auto token = f1._throttler->try_acquire_one();
    EXPECT_TRUE(token.valid());
    EXPECT_EQUAL(f1._throttler->current_active_token_count(), 1u);
}

TEST_F("token can be moved and reset", DynamicThrottleFixture()) {
    auto token1 = f1._throttler->try_acquire_one();
    auto token2 = std::move(token1); // move ctor
    EXPECT_TRUE(token2.valid());
    EXPECT_FALSE(token1.valid());
    ThrottleToken token3;
    token3 = std::move(token2); // move assignment op
    EXPECT_TRUE(token3.valid());
    EXPECT_FALSE(token2.valid());

    // Trying to fetch new token should not succeed due to active token and win size of 1
    token1 = f1._throttler->try_acquire_one();
    EXPECT_FALSE(token1.valid());
    // Resetting the token should free up the slot in the window
    token3.reset();
    token1 = f1._throttler->try_acquire_one();
    EXPECT_TRUE(token1.valid());
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

TEST_F("window size changes dynamically based on throughput", WindowFixture()) {
    uint32_t window_size = f1.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 90 && window_size <= 105);

    window_size = f1.attempt_converge_on_stable_window_size(200);
    ASSERT_TRUE(window_size >= 180 && window_size <= 205);

    window_size = f1.attempt_converge_on_stable_window_size(50);
    ASSERT_TRUE(window_size >= 45 && window_size <= 55);

    window_size = f1.attempt_converge_on_stable_window_size(500);
    ASSERT_TRUE(window_size >= 450 && window_size <= 505);

    window_size = f1.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 90 && window_size <= 115);
}

TEST_F("window size is reset after idle time period", WindowFixture(5, 1)) {
    double window_size = f1.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 90 && window_size <= 110);

    f1._milli_time += 30001; // Not yet past 60s idle time
    auto tokens = f1.fill_entire_throttle_window();
    ASSERT_TRUE(tokens.size() >= 90 && tokens.size() <= 110);
    tokens.clear();

    f1._milli_time += 60001; // Idle time passed
    tokens = f1.fill_entire_throttle_window();
    EXPECT_EQUAL(tokens.size(), 1u); // Reduced to minimum window size
}

TEST_F("minimum window size is respected", WindowFixture(5, 150, INT_MAX)) {
    double window_size = f1.attempt_converge_on_stable_window_size(200);
    ASSERT_TRUE(window_size >= 150 && window_size <= 210);
}

TEST_F("maximum window size is respected", WindowFixture(5, 1, 50)) {
    double window_size = f1.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 40 && window_size <= 50);
}

}

TEST_MAIN() {
    TEST_RUN_ALL();
}
