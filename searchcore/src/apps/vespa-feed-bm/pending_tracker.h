// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>
#include <memory>

namespace storage::spi { struct PersistenceProvider; }

namespace feedbm {

class BucketInfoQueue;

/*
 * Class to track number of pending operations, used as backpressure during
 * benchmark feeding.
 */
class PendingTracker {
    std::atomic<uint32_t>   _pending;
    uint32_t                _limit;
    std::unique_ptr<BucketInfoQueue> _bucket_info_queue;

public:
    PendingTracker(uint32_t limit);
    ~PendingTracker();

    void release() {
        _pending--;
    }
    void retain();
    void drain();

    void attach_bucket_info_queue(storage::spi::PersistenceProvider& provider, std::atomic<uint32_t>& errors);
    BucketInfoQueue *get_bucket_info_queue() { return _bucket_info_queue.get(); }
};

}
