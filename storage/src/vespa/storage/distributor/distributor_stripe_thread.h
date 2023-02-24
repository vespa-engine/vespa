// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/time.h>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <vector>

namespace storage::distributor {

class DistributorStripe;
class DistributorStripePool;
class TickableStripe;

/**
 * A DistributorStripeThread provides threading resources for a single distributor stripe
 * and the means of synchronizing access towards it through a DistributorStripePool.
 *
 * A DistributorStripeThread instance is bidirectionally bound to a particular pool and
 * should therefore always be created by the pool itself (never standalone).
 */
class DistributorStripeThread {
    using AtomicDuration = std::atomic<vespalib::duration>;

    TickableStripe&         _stripe;
    DistributorStripePool&  _stripe_pool;
    AtomicDuration          _tick_wait_duration;
    std::mutex              _mutex;
    std::condition_variable _event_cond;
    std::condition_variable _park_cond;
    std::atomic<uint32_t>   _ticks_before_wait;
    std::atomic<bool>       _should_park;
    std::atomic<bool>       _should_stop;
    bool                    _waiting_for_event;

    friend class DistributorStripePool;
public:
    DistributorStripeThread(TickableStripe& stripe,
                            DistributorStripePool& stripe_pool);
    ~DistributorStripeThread();

    void run();

    // Wakes up stripe thread if it's currently waiting for an external event to be triggered,
    // such as the arrival of a new RPC message. If thread is parked this call will have no
    // effect.
    void notify_event_has_triggered() noexcept;

    void set_tick_wait_duration(vespalib::duration new_tick_wait_duration) noexcept;
    void set_ticks_before_wait(uint32_t new_ticks_before_wait) noexcept;

    TickableStripe*       operator->() noexcept       { return &_stripe; }
    const TickableStripe* operator->() const noexcept { return &_stripe; }

    TickableStripe& stripe() noexcept             { return _stripe; }
    const TickableStripe& stripe() const noexcept { return _stripe; }
private:
    [[nodiscard]] bool should_stop_thread_relaxed() const noexcept {
        return _should_stop.load(std::memory_order_relaxed);
    }

    [[nodiscard]] bool should_park_relaxed() const noexcept {
        return _should_park.load(std::memory_order_relaxed);
    }

    [[nodiscard]] vespalib::duration tick_wait_duration_relaxed() const noexcept {
        return _tick_wait_duration.load(std::memory_order_relaxed);
    }

    [[nodiscard]] uint32_t ticks_before_wait_relaxed() const noexcept {
        return _ticks_before_wait.load(std::memory_order_relaxed);
    }

    void signal_wants_park() noexcept;
    void unpark_thread() noexcept;
    void wait_until_event_notified_or_timed_out() noexcept;
    void wait_until_unparked() noexcept;

    void signal_should_stop() noexcept;
};

}
