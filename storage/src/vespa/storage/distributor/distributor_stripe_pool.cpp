// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "distributor_stripe_pool.h"
#include "distributor_stripe_thread.h"
#include <vespa/storage/common/bucket_stripe_utils.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

namespace storage::distributor {

DistributorStripePool::DistributorStripePool()
    : _thread_pool(512_Ki),
      _n_stripe_bits(0),
      _stripes(),
      _threads(),
      _mutex(),
      _parker_cond(),
      _parked_threads(0),
      _bootstrap_tick_wait_duration(1ms),
      _bootstrap_ticks_before_wait(10),
      _stopped(false)
{}

DistributorStripePool::~DistributorStripePool() {
    if (!_stopped) {
        stop_and_join();
    }
}

void DistributorStripePool::park_all_threads() noexcept {
    assert(!_stripes.empty());
    // Thread pool is not dynamic and signal_wants_park() is thread safe.
    for (auto& s : _stripes) {
        s->signal_wants_park();
    }
    std::unique_lock lock(_mutex);
    _parker_cond.wait(lock, [this]{ return (_parked_threads == _threads.size()); });
}

void DistributorStripePool::unpark_all_threads() noexcept {
    // Thread pool is not dynamic and unpark_thread() is thread safe.
    for (auto& s : _stripes) {
        s->unpark_thread();
    }
    // We have a full unpark barrier here as a pragmatic way to avoid potential ABA issues
    // caused by back-to-back park->unpark->park calls causing issues with interleaving
    // up-counts and down-counts for thread parking/unparking.
    // It's fully possibly to avoid this, but requires a somewhat more finicky solution for
    // cross-thread coordination.
    std::unique_lock lock(_mutex);
    _parker_cond.wait(lock, [this]{ return (_parked_threads == 0); });
}

const TickableStripe& DistributorStripePool::stripe_of_key(uint64_t key) const noexcept {
    return stripe_thread(stripe_of_bucket_key(key, _n_stripe_bits)).stripe();
}

TickableStripe& DistributorStripePool::stripe_of_key(uint64_t key) noexcept {
    return stripe_thread(stripe_of_bucket_key(key, _n_stripe_bits)).stripe();
}

void DistributorStripePool::park_thread_until_released(DistributorStripeThread& thread) noexcept {
    std::unique_lock lock(_mutex);
    assert(_parked_threads < _threads.size());
    ++_parked_threads;
    if (_parked_threads == _threads.size()) {
        _parker_cond.notify_all();
    }
    lock.unlock();
    thread.wait_until_unparked();
    lock.lock();
    --_parked_threads;
    if (_parked_threads == 0) {
        _parker_cond.notify_all();
    }
};

void DistributorStripePool::start(const std::vector<TickableStripe*>& stripes) {
    assert(!stripes.empty());
    assert(_stripes.empty() && _threads.empty());
    assert(stripes.size() == adjusted_num_stripes(stripes.size()));
    _n_stripe_bits = calc_num_stripe_bits(stripes.size());
    _stripes.reserve(stripes.size());
    _threads.reserve(stripes.size());

    for (auto* s : stripes) {
        auto new_stripe = std::make_unique<DistributorStripeThread>(*s, *this);
        new_stripe->set_tick_wait_duration(_bootstrap_tick_wait_duration);
        new_stripe->set_ticks_before_wait(_bootstrap_ticks_before_wait);
        _stripes.emplace_back(std::move(new_stripe));
    }
    for (auto& s : _stripes) {
        _threads.emplace_back(_thread_pool.NewThread(s.get()));
    }
}

void DistributorStripePool::stop_and_join() {
    for (auto& s : _stripes) {
        s->signal_should_stop();
    }
    for (auto* t : _threads) {
        t->Join();
    }
    _stopped = true;
}

void DistributorStripePool::set_tick_wait_duration(vespalib::duration new_tick_wait_duration) noexcept {
    _bootstrap_tick_wait_duration = new_tick_wait_duration;
    // Stripe set may be empty if start() hasn't been called yet.
    for (auto& s : _stripes) {
        s->set_tick_wait_duration(new_tick_wait_duration);
    }
}
void DistributorStripePool::set_ticks_before_wait(uint32_t new_ticks_before_wait) noexcept {
    _bootstrap_ticks_before_wait = new_ticks_before_wait;
    // Stripe set may be empty if start() hasn't been called yet.
    for (auto& s : _stripes) {
        s->set_ticks_before_wait(new_ticks_before_wait);
    }
}

}
