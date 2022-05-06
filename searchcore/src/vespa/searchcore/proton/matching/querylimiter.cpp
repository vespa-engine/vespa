// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "querylimiter.h"
#include <chrono>

namespace proton:: matching {

QueryLimiter::LimitedToken::LimitedToken(const Doom & doom, QueryLimiter & limiter) :
    _limiter(limiter)
{
    _limiter.grabToken(doom);
}

QueryLimiter::LimitedToken::~LimitedToken()
{
    _limiter.releaseToken();
}

void
QueryLimiter::grabToken(const Doom & doom)
{
    std::unique_lock<std::mutex> guard(_lock);
    for (auto max_threads = get_max_threads(); (max_threads > 0) && (_activeThreads >= max_threads) && !doom.hard_doom(); max_threads = get_max_threads()) {
        vespalib::duration left = doom.hard_left();
        if (left > vespalib::duration::zero()) {
            _cond.wait_for(guard, left);
        }
    }
    _activeThreads++;
}

void
QueryLimiter::releaseToken()
{
    std::lock_guard<std::mutex> guard(_lock);
    _activeThreads--;
    _cond.notify_one();
}

QueryLimiter::QueryLimiter() :
    _lock(),
    _cond(),
    _activeThreads(0),
    _maxThreads(-1),
    _coverage(1.0),
    _minHits(std::numeric_limits<uint32_t>::max())
{
}

void
QueryLimiter::configure(int maxThreads, double coverage, uint32_t minHits)
{
    std::lock_guard<std::mutex> guard(_lock);
    _maxThreads.store(maxThreads, std::memory_order_relaxed);
    _coverage.store(coverage, std::memory_order_relaxed);
    _minHits.store(minHits, std::memory_order_relaxed);
    _cond.notify_all();
}

QueryLimiter::Token::UP
QueryLimiter::getToken(const Doom & doom, uint32_t numDocs, uint32_t numHits, bool hasSorting, bool hasGrouping)
{
    if (get_max_threads() > 0) {
        if (hasSorting || hasGrouping) {
            if (numHits > get_min_hits()) {
                if (numDocs * get_coverage() < numHits) {
                    return std::make_unique<LimitedToken>(doom, *this);
                }
            }
        }
    }
    return std::make_unique<NoLimitToken>();
}

}
