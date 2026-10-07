// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "querylimiter.h"

#include <algorithm>
#include <cassert>
#include <chrono>
#include <limits>

namespace proton::matching {

QueryLimiter::LimitedToken::LimitedToken(const Doom& doom, QueryLimiter& limiter, int units)
    : _limiter(limiter), _units(_limiter.grabToken(doom, units)) {
}

QueryLimiter::LimitedToken::~LimitedToken() {
    _limiter.releaseToken(_units);
}

int QueryLimiter::grant_for(int units) const noexcept {
    int max_threads = get_max_threads();
    return (max_threads > 0) ? std::min(units, max_threads) : units;
}

bool QueryLimiter::fits(int granted) const noexcept {
    int max_threads = get_max_threads();
    return (max_threads <= 0) || (_activeThreads + granted <= max_threads);
}

/*
 * Grants waiters from the front of the queue, in order, while their requests,
 * capped at the current max, fit. Each grant is charged here, once, and the
 * waiter returns it as is. Every release, reconfiguration and departure from
 * the front calls this, so whenever _lock is free, the queue is empty or its
 * front does not fit.
 */
void QueryLimiter::grant_front() {
    while (!_waiting.empty()) {
        Waiter& front = *_waiting.front();
        int     granted = grant_for(front.units);
        if (!fits(granted)) {
            break;
        }
        _waiting.pop_front();
        _activeThreads += granted;
        front.granted = granted;
        front.done = true;
        front.cond.notify_one();
    }
}

int QueryLimiter::grabToken(const Doom& doom, int units) {
    std::unique_lock<std::mutex> guard(_lock);
    int                          granted = grant_for(units);
    // a queued waiter always goes before a newcomer
    if (doom.hard_doom() || (_waiting.empty() && fits(granted))) {
        _activeThreads += granted;
        return granted;
    }
    Waiter me(units);
    _waiting.push_back(&me);
    // Leaves the queue on any exit without a grant. Constructed after the
    // waiter is queued, and destroyed before the waiter and the lock. It
    // cannot undo a grant, so nothing that can throw may run between a grant
    // and its return.
    struct Dequeue {
        QueryLimiter& self;
        Waiter&       me;
        ~Dequeue() {
            if (me.done) {
                return; // removed from the queue when granted
            }
            auto pos = std::find(self._waiting.begin(), self._waiting.end(), &me);
            assert(pos != self._waiting.end());
            bool was_front = (pos == self._waiting.begin());
            self._waiting.erase(pos);
            if (was_front) {
                self.grant_front();
            }
        }
    } dequeue{*this, me};
    while (!me.done) {
        if (doom.hard_doom()) {
            granted = grant_for(units); // max may have changed while queued
            _activeThreads += granted;
            return granted;
        }
        // doom uses a coarse clock; avoid spinning while it catches up, and
        // avoid overflow in wait_for for a doom far in the future
        me.cond.wait_for(guard, std::clamp(doom.hard_left(), vespalib::duration(std::chrono::milliseconds(1)),
                                           vespalib::duration(std::chrono::seconds(1))));
    }
    return me.granted;
}

void QueryLimiter::releaseToken(int units) {
    std::lock_guard<std::mutex> guard(_lock);
    _activeThreads -= units;
    grant_front();
}

QueryLimiter::QueryLimiter()
    : _lock(),
      _activeThreads(0),
      _waiting(),
      _maxThreads(-1),
      _coverage(1.0),
      _minHits(std::numeric_limits<uint32_t>::max()) {
}

void QueryLimiter::configure(int maxThreads, double coverage, uint32_t minHits) {
    std::lock_guard<std::mutex> guard(_lock);
    _maxThreads.store(maxThreads, std::memory_order_relaxed);
    _coverage.store(coverage, std::memory_order_relaxed);
    _minHits.store(minHits, std::memory_order_relaxed);
    grant_front();
}

QueryLimiter::Token::UP QueryLimiter::getToken(const Doom& doom, uint32_t numDocs, uint32_t numHits, bool hasSorting,
                                               bool hasGrouping) {
    if (get_max_threads() > 0) {
        if (hasSorting || hasGrouping) {
            if (numHits > get_min_hits()) {
                if (numDocs * get_coverage() < numHits) {
                    return std::make_unique<LimitedToken>(doom, *this, 1);
                }
            }
        }
    }
    return std::make_unique<NoLimitToken>();
}

QueryLimiter::Token::UP QueryLimiter::getQueryToken(const Doom& doom, uint32_t numThreads) {
    if (get_max_threads() > 0) {
        int units = static_cast<int>(std::clamp<uint32_t>(numThreads, 1, std::numeric_limits<int>::max()));
        return std::make_unique<LimitedToken>(doom, *this, units);
    }
    return std::make_unique<NoLimitToken>();
}

int QueryLimiter::active_threads() {
    std::lock_guard<std::mutex> guard(_lock);
    return _activeThreads;
}

size_t QueryLimiter::num_waiting() {
    std::lock_guard<std::mutex> guard(_lock);
    return _waiting.size();
}

} // namespace proton::matching
