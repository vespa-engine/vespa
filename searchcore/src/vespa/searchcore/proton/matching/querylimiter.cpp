// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "querylimiter.h"

namespace proton {
namespace matching {

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
    vespalib::MonitorGuard guard(_monitor);
    while ((_maxThreads > 0) && (_activeThreads >= _maxThreads) && !doom.doom()) {
        int left = doom.left().ms();
        if (left > 0) {
            guard.wait(left);
        }
    }
    _activeThreads++;
}

void
QueryLimiter::releaseToken()
{
    vespalib::MonitorGuard guard(_monitor);
    _activeThreads--;
    guard.signal();
}

QueryLimiter::QueryLimiter() :
    _monitor(),
    _activeThreads(0),
    _maxThreads(-1),
    _coverage(1.0),
    _minHits(std::numeric_limits<uint32_t>::max())
{
}

void
QueryLimiter::configure(int maxThreads, double coverage, uint32_t minHits)
{
    _maxThreads = maxThreads;
    _coverage = coverage;
    _minHits = minHits;
}

QueryLimiter::Token::UP
QueryLimiter::getToken(const Doom & doom, uint32_t numDocs, uint32_t numHits, bool hasSorting, bool hasGrouping)
{
    if (_maxThreads > 0) {
        if (hasSorting || hasGrouping) {
            if (numHits > _minHits) {
                if (numDocs * _coverage < numHits) {
                    return Token::UP(new LimitedToken(doom, *this));
                }
            }
        }
    }
    return Token::UP(new NoLimitToken());
}

}
}
