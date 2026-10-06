// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "querylimiter.h"

#include <algorithm>
#include <chrono>
#include <limits>

namespace proton::matching {

QueryLimiter::LimitedToken::LimitedToken(const Doom& doom, QueryLimiter& limiter, int units)
    : _limiter(limiter), _units(_limiter.grabToken(doom, units)) {
}

QueryLimiter::LimitedToken::~LimitedToken() {
    _limiter.releaseToken(_units);
}

int QueryLimiter::grabToken(const Doom& doom, int units) {
    std::unique_lock<std::mutex> guard(_lock);
    uint64_t                     my_ticket = _nextTicket++;
    _waiting.push_back(my_ticket);
    for (;;) {
        int max_threads = get_max_threads();
        // re-evaluated on each wakeup since max may change at reconfig
        int  granted = (max_threads > 0) ? std::min(units, max_threads) : units;
        bool at_front = (_waiting.front() == my_ticket);
        if ((max_threads <= 0) || doom.hard_doom() || (at_front && (_activeThreads + granted <= max_threads))) {
            _waiting.erase(std::find(_waiting.begin(), _waiting.end(), my_ticket));
            _activeThreads += granted;
            // the new front of the queue may now be able to proceed
            _cond.notify_all();
            return granted;
        }
        // doom uses a coarse clock; avoid spinning while it catches up, and
        // avoid overflow in wait_for for a doom far in the future
        _cond.wait_for(guard, std::clamp(doom.hard_left(), vespalib::duration(std::chrono::milliseconds(1)),
                                         vespalib::duration(std::chrono::seconds(1))));
    }
}

void QueryLimiter::releaseToken(int units) {
    std::lock_guard<std::mutex> guard(_lock);
    _activeThreads -= units;
    _cond.notify_all();
}

QueryLimiter::QueryLimiter()
    : _lock(),
      _cond(),
      _activeThreads(0),
      _nextTicket(0),
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
    _cond.notify_all();
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
