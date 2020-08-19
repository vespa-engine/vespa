// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>
#include <condition_variable>

namespace proton {

class PendingLidTracker {
public:
    PendingLidTracker();
    ~PendingLidTracker();
    void produce(uint32_t lid);
    void consume(uint32_t lid);
    void waitForConsumedLid(uint32_t lid);
private:
    std::mutex _mutex;
    std::condition_variable _cond;
    vespalib::hash_map<uint32_t, uint32_t> _pending;
};

}
