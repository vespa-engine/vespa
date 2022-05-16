// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributor_stripe_thread.h"
#include "distributor_stripe.h"
#include "distributor_stripe_pool.h"
#include "tickable_stripe.h"
#include <cassert>

namespace storage::distributor {

DistributorStripeThread::DistributorStripeThread(TickableStripe& stripe,
                                                 DistributorStripePool& stripe_pool)
    : _stripe(stripe),
      _stripe_pool(stripe_pool),
      _tick_wait_duration(vespalib::adjustTimeoutByDetectedHz(1ms)),
      _mutex(),
      _event_cond(),
      _park_cond(),
      _ticks_before_wait(10),
      _should_park(false),
      _should_stop(false),
      _waiting_for_event(false)
{}

DistributorStripeThread::~DistributorStripeThread() = default;

void DistributorStripeThread::Run(FastOS_ThreadInterface*, void*) {
    uint32_t tick_waits_inhibited = 0;
    while (!should_stop_thread_relaxed()) {
        while (should_park_relaxed()) {
            _stripe_pool.park_thread_until_released(*this);
        }
        // TODO consider enum to only trigger "ticks before wait"-behavior when maintenance was done
        const bool did_work = _stripe.tick();
        if (did_work) {
            tick_waits_inhibited = 0;
        } else if (tick_waits_inhibited >= ticks_before_wait_relaxed()) {
            wait_until_event_notified_or_timed_out();
            tick_waits_inhibited = 0;
        } else {
            ++tick_waits_inhibited;
        }
    }
}

void DistributorStripeThread::signal_wants_park() noexcept {
    std::lock_guard lock(_mutex);
    assert(!should_park_relaxed());
    _should_park.store(true, std::memory_order_relaxed);
    if (_waiting_for_event) {
        _event_cond.notify_one();
    }
}

void DistributorStripeThread::unpark_thread() noexcept {
    std::lock_guard lock(_mutex);
    assert(should_park_relaxed());
    _should_park.store(false, std::memory_order_relaxed);
    _park_cond.notify_one();
}

void DistributorStripeThread::wait_until_event_notified_or_timed_out() noexcept {
    std::unique_lock lock(_mutex);
    if (should_stop_thread_relaxed() || should_park_relaxed()) {
        return;
    }
    _waiting_for_event = true;
    _event_cond.wait_for(lock, tick_wait_duration_relaxed());
    _waiting_for_event = false;
}

void DistributorStripeThread::wait_until_unparked() noexcept {
    std::unique_lock lock(_mutex);
    // _should_park is always written within _mutex, relaxed load is safe.
    _park_cond.wait(lock, [this]{ return !should_park_relaxed(); });
}

void DistributorStripeThread::notify_event_has_triggered() noexcept {
    // TODO mutex protect and add flag for "should tick immediately next time"
    // TODO only notify if _waiting_for_event == true
    _event_cond.notify_one();
}

void DistributorStripeThread::signal_should_stop() noexcept {
    std::unique_lock lock(_mutex);
    assert(!should_park_relaxed());
    _should_stop.store(true, std::memory_order_relaxed);
    if (_waiting_for_event) {
        _event_cond.notify_one();
    }
    // TODO if we ever need it, handle pending thread park. For now we assume that
    //   the caller never attempts to concurrently park and stop threads.
}

void DistributorStripeThread::set_tick_wait_duration(vespalib::duration new_tick_wait_duration) noexcept {
    static_assert(AtomicDuration::is_always_lock_free);
    // No memory ordering required for a "lazy" single value setting such as the tick duration
    _tick_wait_duration.store(new_tick_wait_duration, std::memory_order_relaxed);
}

void DistributorStripeThread::set_ticks_before_wait(uint32_t new_ticks_before_wait) noexcept {
    _ticks_before_wait.store(new_ticks_before_wait, std::memory_order_relaxed);
}


}
