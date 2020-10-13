// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <condition_variable>
#include <atomic>
#include <memory>

namespace storage::spi { class PersistenceProvider; }

namespace feedbm {

class BucketInfoQueue;

/*
 * Class to track number of pending operations, used as backpressure during
 * benchmark feeding.
 */
class PendingTracker {
    uint32_t                _pending;
    uint32_t                _limit;
    std::mutex              _mutex;
    std::condition_variable _cond;
    std::unique_ptr<BucketInfoQueue> _bucket_info_queue;

public:
    PendingTracker(uint32_t limit);
    ~PendingTracker();

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

    void drain();

    void attach_bucket_info_queue(storage::spi::PersistenceProvider& provider, std::atomic<uint32_t>& errors);
    BucketInfoQueue *get_bucket_info_queue() { return _bucket_info_queue.get(); }
};

}
