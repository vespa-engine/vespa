// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucket.h>
#include <mutex>
#include <deque>
#include <atomic>

namespace storage::spi { struct PersistenceProvider; }

namespace search::bmcluster {

/*
 * Class containing a queue of buckets where mutating feed operations
 * have been performed, requiring service layer to ask for updated
 * bucket info.
 */
class BucketInfoQueue
{
    using PendingGetBucketInfo = std::pair<storage::spi::Bucket, storage::spi::PersistenceProvider*>;
    std::mutex                         _mutex;
    std::deque<PendingGetBucketInfo>   _pending_get_bucket_infos;
    std::atomic<uint32_t>&             _errors;

public:
    BucketInfoQueue(std::atomic<uint32_t>& errors);
    ~BucketInfoQueue();

    void put_bucket(storage::spi::Bucket bucket, storage::spi::PersistenceProvider* provider)
    {
        std::lock_guard guard(_mutex);
        _pending_get_bucket_infos.emplace_back(std::make_pair(std::move(bucket), provider));
    }

    void get_bucket_info_loop();
};

}
