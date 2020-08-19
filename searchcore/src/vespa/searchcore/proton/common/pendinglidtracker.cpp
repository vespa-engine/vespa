// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pendinglidtracker.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

namespace proton {

PendingLidTracker::PendingLidTracker()
    : _mutex(),
      _cond(),
      _pending()
{}

PendingLidTracker::~PendingLidTracker() {
    assert(_pending.empty());
}

void
PendingLidTracker::produce(uint32_t lid) {
    std::lock_guard guard(_mutex);
    _pending[lid]++;
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
PendingLidTracker::consume(Snapshot && lids) {
    std::lock_guard guard(_mutex);
    for (auto entry : lids) {
        auto found = _pending.find(entry.first);
        assert (found != _pending.end());
        assert (found->second > 0);
        if (found->second == entry.second) {
            _pending.erase(found);
        } else {
            found->second -= entry.second;
        }
    }
    _cond.notify_all();
}

PendingLidTracker::Snapshot
PendingLidTracker::snapshot() {
    Snapshot snapshot;
    std::lock_guard guard(_mutex);
    snapshot.reserve(_pending.size());
    for (auto entry : _pending) {
        snapshot.emplace_back(entry.first, entry.second);
    }
    return snapshot;
}

void
PendingLidTracker::waitFor(MonitorGuard & guard, uint32_t lid) {
    while (_pending.find(lid) != _pending.end()) {
        _cond.wait(guard);
    }
}

void
PendingLidTracker::waitForConsumedLid(uint32_t lid) {
    MonitorGuard guard(_mutex);
    waitFor(guard, lid);
}

void
PendingLidTracker::waitForConsumedLid(const std::vector<uint32_t> & lids) {
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
PendingLidTracker::isInFlight(const std::vector<uint32_t> & lids) {
    MonitorGuard guard(_mutex);
    for (uint32_t lid : lids) {
        if (_pending.find(lid) != _pending.end()) {
            return true;
        }
    }
    return false;
}

}
