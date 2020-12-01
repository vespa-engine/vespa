// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pending_tracker.h"
#include "bucket_info_queue.h"
#include <thread>
#include <chrono>

using namespace std::chrono_literals;

namespace feedbm {

PendingTracker::PendingTracker(uint32_t limit)
    : _pending(0u),
      _limit(limit),
      _bucket_info_queue()
{
}

PendingTracker::~PendingTracker()
{
    drain();
}

void
PendingTracker::retain() {
    while (_pending >= _limit) {
        std::this_thread::sleep_for(1ms);
    }
    _pending++;
}

void
PendingTracker::drain()
{
    if (_bucket_info_queue) {
        _bucket_info_queue->get_bucket_info_loop();
    }
    while (_pending > 0) {
        std::this_thread::sleep_for(1ms);
        if (_bucket_info_queue) {
            _bucket_info_queue->get_bucket_info_loop();
        }
    }
    if (_bucket_info_queue) {
        _bucket_info_queue->get_bucket_info_loop();
    }
}

void
PendingTracker::attach_bucket_info_queue(storage::spi::PersistenceProvider& provider, std::atomic<uint32_t>& errors)
{
    _bucket_info_queue = std::make_unique<BucketInfoQueue>(provider, errors);
}

}
