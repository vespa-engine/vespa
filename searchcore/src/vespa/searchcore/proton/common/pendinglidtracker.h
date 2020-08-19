// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <condition_variable>
#include <vector>

namespace proton {

class PendingLidTracker {
public:
    using Snapshot = std::vector<std::pair<uint32_t, uint32_t>>;
    PendingLidTracker();
    ~PendingLidTracker();
    void produce(uint32_t lid);
    void consume(uint32_t lid);
    void consume(Snapshot && lids);
    Snapshot snapshot();
    void waitForConsumedLid(uint32_t lid);
    void waitForConsumedLid(const std::vector<uint32_t> & lids);
    bool isInFlight(uint32_t lid);
    bool isInFlight(const std::vector<uint32_t> & lids);
private:
    using MonitorGuard = std::unique_lock<std::mutex>;
    void waitFor(MonitorGuard & guard, uint32_t lid);
    std::mutex                             _mutex;
    std::condition_variable                _cond;
    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

}
