// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "abstract_bucket_map.h"
#include "read_guard.h"
#include "storagebucketinfo.h"
#include <vespa/storageapi/defs.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <memory>

namespace storage {

struct ContentBucketDbOptions;

class StorBucketDatabase {
    std::unique_ptr<bucketdb::AbstractBucketMap<bucketdb::StorageBucketInfo>> _impl;
public:
    using Entry        = bucketdb::StorageBucketInfo;
    using BucketMap    = bucketdb::AbstractBucketMap<Entry>;
    using key_type     = BucketMap::key_type;
    using Decision     = BucketMap::Decision;
    using WrappedEntry = BucketMap::WrappedEntry;
    using EntryMap     = BucketMap::EntryMap;
    using BucketId     = document::BucketId;

    enum Flag {
        NONE = 0,
        CREATE_IF_NONEXISTING = 1
    };

    explicit StorBucketDatabase(const ContentBucketDbOptions&);

    void insert(const document::BucketId&, const Entry&, const char* clientId);

    bool erase(const document::BucketId&, const char* clientId);

    WrappedEntry get(const document::BucketId& bucket, const char* clientId, Flag flags = NONE);

    size_t size() const;

    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket, and all buckets that that bucket contains.
     */
    EntryMap getAll(const BucketId& bucketId, const char* clientId);

    /**
     * Returns all buckets in the bucket database that can contain the given
     * bucket. Usually, there should be only one such bucket, but in the case
     * of inconsistent splitting, there may be more than one.
     */
    EntryMap getContained(const BucketId& bucketId, const char* clientId);

    /**
     * Iterate over the entire database contents, holding the global database
     * mutex for `chunkSize` processed entries at a time, yielding the current
     * thread between each such such to allow other threads to get a chance
     * at acquiring a bucket lock.
     */
    void for_each_chunked(std::function<Decision(uint64_t, const Entry &)> func, const char* clientId,
                          vespalib::duration yieldTime = 10us, uint32_t chunkSize = BucketMap::DEFAULT_CHUNK_SIZE);

    void for_each_mutable_unordered(std::function<Decision(uint64_t, Entry &)> func, const char* clientId);

    void for_each(std::function<Decision(uint64_t, const Entry &)> func, const char* clientId);

    [[nodiscard]] std::unique_ptr<bucketdb::ReadGuard<Entry>> acquire_read_guard() const;

    /**
     * Returns true iff bucket has no superbuckets or sub-buckets in the
     * database. Usage assumption is that any operation that can cause the
     * bucket to become inconsistent will require taking its lock, so by
     * requiring the lock to be provided here we avoid race conditions.
     */
    bool isConsistent(const WrappedEntry& entry);

    [[nodiscard]] size_t getMemoryUsage() const;
    [[nodiscard]] vespalib::MemoryUsage detailed_memory_usage() const noexcept;
    void showLockClients(vespalib::asciistream & out) const;

};

} // storage

