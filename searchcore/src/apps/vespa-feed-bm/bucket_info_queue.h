// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <mutex>
#include <deque>
#include <atomic>

namespace storage::spi { class PersistenceProvider; }

namespace feedbm {

/*
 * Class containing a queue of buckets where mutating feed operations
 * have been performed, requiring service layer to ask for updated
 * bucket info.
 */
class BucketInfoQueue
{
    std::mutex                         _mutex;
    std::deque<storage::spi::Bucket>   _buckets;
    storage::spi::PersistenceProvider& _provider;
    std::atomic<uint32_t>&             _errors;

public:
    BucketInfoQueue(storage::spi::PersistenceProvider& provider, std::atomic<uint32_t>& errors);
    ~BucketInfoQueue();

    void put_bucket(storage::spi::Bucket bucket)
    {
        std::lock_guard guard(_mutex);
        _buckets.emplace_back(std::move(bucket));
    }

    void get_bucket_info_loop();
};

}
