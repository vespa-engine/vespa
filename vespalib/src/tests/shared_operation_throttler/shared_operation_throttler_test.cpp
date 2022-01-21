// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/shared_operation_throttler.h>
#include <vespa/vespalib/testkit/test_kit.h>

namespace vespalib {

// Note on test semantics: these tests are adapted from a subset of the MessageBus
// throttling tests. Some tests have been simplified due to no longer having access
// to the low-level DynamicThrottlePolicy API.

struct Fixture {
    uint64_t _milli_time;
    std::unique_ptr<SharedOperationThrottler> _throttler;

    Fixture(uint32_t window_size_increment = 5,
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

TEST_F("window size changes dynamically based on throughput", Fixture()) {
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

TEST_F("window size is reset after idle time period", Fixture(5, 1)) {
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

TEST_F("minimum window size is respected", Fixture(5, 150, INT_MAX)) {
    double window_size = f1.attempt_converge_on_stable_window_size(200);
    ASSERT_TRUE(window_size >= 150 && window_size <= 210);
}

TEST_F("maximum window size is respected", Fixture(5, 1, 50)) {
    double window_size = f1.attempt_converge_on_stable_window_size(100);
    ASSERT_TRUE(window_size >= 40 && window_size <= 50);
}

}

TEST_MAIN() {
    TEST_RUN_ALL();
}
