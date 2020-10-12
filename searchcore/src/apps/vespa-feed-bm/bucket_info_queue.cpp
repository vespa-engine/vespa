// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_info_queue.h"
#include <vespa/persistence/spi/persistenceprovider.h>

namespace feedbm {

BucketInfoQueue::BucketInfoQueue(storage::spi::PersistenceProvider& provider, std::atomic<uint32_t>& errors)
    : _mutex(),
      _buckets(),
      _provider(provider),
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
    while (!_buckets.empty()) {
        auto bucket = _buckets.front();
        _buckets.pop_front();
        guard.unlock();
        auto bucket_info = _provider.getBucketInfo(bucket);
        if (bucket_info.hasError()) {
            ++_errors;
        }
        guard.lock();
    }
}

}

