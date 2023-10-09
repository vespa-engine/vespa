// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>

namespace search::bmcluster {

class PendingTracker;

/*
 * Class maintaing mapping from message id to pending tracker
 */
class PendingTrackerHash
{
    std::mutex _mutex;
    vespalib::hash_map<uint64_t, PendingTracker *> _pending;
public:
    PendingTrackerHash();
    ~PendingTrackerHash();
    PendingTracker *release(uint64_t msg_id);
    void retain(uint64_t msg_id, PendingTracker &tracker);
};

}
