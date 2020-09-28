// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <condition_variable>

namespace feedbm {

/*
 * Class to track number of pending operations, used as backpressure during
 * benchmark feeding.
 */
class PendingTracker {
    uint32_t                _pending;
    uint32_t                _limit;
    std::mutex              _mutex;
    std::condition_variable _cond;

public:
    PendingTracker(uint32_t limit)
        : _pending(0u),
          _limit(limit),
          _mutex(),
          _cond()
    {
    }

    ~PendingTracker()
    {
        drain();
    }

    void release() {
        std::unique_lock<std::mutex> guard(_mutex);
        --_pending;
        if (_pending < _limit) {
            _cond.notify_all();
        }
    }
    void retain() {
        std::unique_lock<std::mutex> guard(_mutex);
        while (_pending >= _limit) {
            _cond.wait(guard);
        }
        ++_pending;
    }

    void drain() {
        std::unique_lock<std::mutex> guard(_mutex);
        while (_pending > 0) {
            _cond.wait(guard);
        }
    }
};

}
