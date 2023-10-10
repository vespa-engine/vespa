// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_info_queue.h"
#include <vespa/persistence/spi/persistenceprovider.h>

namespace search::bmcluster {

BucketInfoQueue::BucketInfoQueue(std::atomic<uint32_t>& errors)
    : _mutex(),
      _pending_get_bucket_infos(),
      _errors(errors)
{
}

BucketInfoQueue::~BucketInfoQueue()
{
    get_bucket_info_loop();
}

void
BucketInfoQueue::get_bucket_info_loop()
{
    std::unique_lock guard(_mutex);
    while (!_pending_get_bucket_infos.empty()) {
        auto pending_get_bucket_info = _pending_get_bucket_infos.front();
        _pending_get_bucket_infos.pop_front();
        guard.unlock();
        auto bucket_info = pending_get_bucket_info.second->getBucketInfo(pending_get_bucket_info.first);
        if (bucket_info.hasError()) {
            ++_errors;
        }
        guard.lock();
    }
}

}
