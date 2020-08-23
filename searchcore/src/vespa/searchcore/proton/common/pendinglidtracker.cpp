// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pendinglidtracker.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <algorithm>
#include <cassert>

namespace proton {

IPendingLidTracker::Token::Token(uint32_t lid, IPendingLidTracker &tracker)
    : _tracker(&tracker),
      _lid(lid)
{}

IPendingLidTracker::Token::Token()
    : _tracker(nullptr),
      _lid(0u)
{}


IPendingLidTracker::Token::~Token() {
    if (_tracker != nullptr) {
        _tracker->consume(_lid);
    }
}

IPendingLidTracker::Token
NoopLidTracker::produce(uint32_t) {
    return Token();
}

PendingLidTracker::PendingLidTracker()
    : _mutex(),
      _cond(),
      _pending()
{}

PendingLidTracker::~PendingLidTracker() {
    assert(_pending.empty());
}

PendingLidTracker::Token
PendingLidTracker::produce(uint32_t lid) {
    std::lock_guard guard(_mutex);
    _pending[lid]++;
    return Token(lid, *this);
}
void
PendingLidTracker::consume(uint32_t lid) {
    std::lock_guard guard(_mutex);
    auto found = _pending.find(lid);
    assert (found != _pending.end());
    assert (found->second > 0);
    if (found->second == 1) {
        _pending.erase(found);
        _cond.notify_all();
    } else {
        found->second--;
    }
}

void
PendingLidTracker::waitFor(MonitorGuard & guard, uint32_t lid) {
    while (_pending.find(lid) != _pending.end()) {
        _cond.wait(guard);
    }
}

void
PendingLidTracker::waitForEmpty() {
    MonitorGuard guard(_mutex);
    while ( ! _pending.empty() ) {
        _cond.wait(guard);
    }
}

void
PendingLidTracker::waitForConsumed(uint32_t lid) {
    MonitorGuard guard(_mutex);
    waitFor(guard, lid);
}

void
PendingLidTracker::waitForConsumed(const std::vector<uint32_t> & lids) {
    MonitorGuard guard(_mutex);
    for (uint32_t lid : lids) {
        waitFor(guard, lid);
    }
}

bool
PendingLidTracker::isInFlight(uint32_t lid) {
    MonitorGuard guard(_mutex);
    return _pending.find(lid) != _pending.end();
}

bool
PendingLidTracker::areAnyInFlight(const std::vector<uint32_t> & lids) {
    MonitorGuard guard(_mutex);
    return std::any_of(lids.begin(), lids.end(),
                       [this](uint32_t lid) {
                           return _pending.find(lid) == _pending.end();
                       });
}

bool
PendingLidTracker::areAnyInFlight() {
    MonitorGuard guard(_mutex);
    return !_pending.empty();
}

}
