// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mock_tickable_stripe.h"
#include <vespa/storage/distributor/distributor_stripe_pool.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/time.h>
#include <atomic>
#include <thread>

using namespace ::testing;

namespace storage::distributor {

struct DistributorStripePoolThreadingTest : Test {
    static constexpr vespalib::duration min_test_duration = 50ms;

    DistributorStripePool _pool;
    vespalib::steady_time _start_time;
    std::atomic<bool>     _is_parked;

    DistributorStripePoolThreadingTest()
        : _pool(),
          _start_time(std::chrono::steady_clock::now()),
          _is_parked(false)
    {
        // Set an absurdly high tick wait duration to catch any regressions where
        // thread wakeups aren't triggering as expected.
        _pool.set_tick_wait_duration(600s);
        // Ensure we always trigger a wait if tick() returns false.
        _pool.set_ticks_before_wait(0);
    }

    bool min_test_time_reached() const noexcept {
        return ((std::chrono::steady_clock::now() - _start_time) > min_test_duration);
    }

    void loop_park_unpark_cycle_until_test_time_expired() {
        constexpr size_t min_cycles = 100;
        size_t cycle = 0;
        // TODO enforce minimum number of actual calls to tick() per thread?
        while ((cycle < min_cycles) || !min_test_time_reached()) {
            _pool.park_all_threads();
            _is_parked = true;
            std::this_thread::sleep_for(50us);
            _is_parked = false;
            _pool.unpark_all_threads();
            ++cycle;
        }
    }
};

// Optimistic invariant checker that cannot prove correctness, but will hopefully
// make tests scream if something is obviously incorrect.
struct ParkingInvariantCheckingMockStripe : MockTickableStripe {
    std::atomic<bool>& _is_parked;
    bool               _to_return;

    explicit ParkingInvariantCheckingMockStripe(std::atomic<bool>& is_parked)
        : _is_parked(is_parked),
          _to_return(true)
    {}

    bool tick() override {
        std::this_thread::sleep_for(50us);
        assert(!_is_parked.load());
        // Alternate between returning whether or not work was done to trigger
        // both waiting and non-waiting edges. Note that this depends on the
        // ticks_before_wait value being 0.
        _to_return = !_to_return;
        return _to_return;
    }
};

TEST_F(DistributorStripePoolThreadingTest, can_park_and_unpark_single_stripe) {
    ParkingInvariantCheckingMockStripe stripe(_is_parked);

    _pool.start({&stripe});
    loop_park_unpark_cycle_until_test_time_expired();
    _pool.stop_and_join();
}

TEST_F(DistributorStripePoolThreadingTest, can_park_and_unpark_multiple_stripes) {
    ParkingInvariantCheckingMockStripe s1(_is_parked);
    ParkingInvariantCheckingMockStripe s2(_is_parked);
    ParkingInvariantCheckingMockStripe s3(_is_parked);
    ParkingInvariantCheckingMockStripe s4(_is_parked);

    _pool.start({{&s1, &s2, &s3, &s4}});
    loop_park_unpark_cycle_until_test_time_expired();
    _pool.stop_and_join();
}

}
