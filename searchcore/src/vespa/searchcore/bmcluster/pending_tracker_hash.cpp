// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pending_tracker_hash.h"
#include "pending_tracker.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

namespace search::bmcluster {

PendingTrackerHash::PendingTrackerHash()
    : _mutex(),
      _pending()
{
}

PendingTrackerHash::~PendingTrackerHash()
{
    std::lock_guard lock(_mutex);
    assert(_pending.empty());
}

void
PendingTrackerHash::retain(uint64_t msg_id, PendingTracker &tracker)
{
    tracker.retain();
    std::lock_guard lock(_mutex);
    _pending.insert(std::make_pair(msg_id, &tracker));
}

PendingTracker *
PendingTrackerHash::release(uint64_t msg_id)
{
    std::lock_guard lock(_mutex);
    auto itr = _pending.find(msg_id);
    if (itr == _pending.end()) {
        return nullptr;
    }
    auto tracker = itr->second;
    _pending.erase(itr);
    return tracker;
}

}
