// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketstate.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/persistence/spi/result.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <atomic>

namespace proton::bucketdb { class RemoveBatchEntry; }

namespace proton {

class BucketDB
{
private:
    using GlobalId = document::GlobalId;
    using BucketId = document::BucketId;
    using Timestamp = storage::spi::Timestamp;
    using BucketChecksum = storage::spi::BucketChecksum;
    using BucketState = bucketdb::BucketState;
    using Map = vespalib::hash_map<BucketId, BucketState, document::BucketId::hash>;

    Map                  _map;
    std::atomic<size_t>  _numActiveDocs;
    BucketId             _cachedBucketId;
    BucketState          _cachedBucketState;

    void clear();
    void checkEmpty() const;
    size_t countActiveDocs() const;
    void checkActiveCount() const;
    void addActive(size_t value) {
        _numActiveDocs.store(getNumActiveDocs() + value, std::memory_order_relaxed);
    }
    void subActive(size_t value) {
        _numActiveDocs.store(getNumActiveDocs() - value, std::memory_order_relaxed);
    }
public:
    BucketDB();
    BucketDB(const BucketDB &) = delete;
    BucketDB & operator=(const BucketDB &) = delete;
    ~BucketDB();

    const BucketState & add(const GlobalId &gid,
                            BucketId bucketId, Timestamp  timestamp, uint32_t docSize,
                            SubDbType subDbType);

    void add(BucketId bucketId, const BucketState & state);
    void remove(const GlobalId &gid,
                BucketId bucketId, Timestamp  timestamp, uint32_t docSize,
                SubDbType subDbType);

    void remove_batch(const std::vector<bucketdb::RemoveBatchEntry> &removed, SubDbType sub_db_type);

    void modify(const GlobalId &gid,
                BucketId oldBucketId, Timestamp  oldTimestamp, uint32_t oldDocSize,
                BucketId newBucketId, Timestamp  newTimestamp, uint32_t newDocSize,
                SubDbType subDbType);

    BucketState get(BucketId bucketId) const;
    void cacheBucket(BucketId bucketId);
    void uncacheBucket();
    bool isCachedBucket(BucketId bucketId) const;
    storage::spi::BucketInfo cachedGetBucketInfo(BucketId bucketId) const;
    BucketState cachedGet(BucketId bucketId) const;
    bool hasBucket(BucketId bucketId) const;
    BucketId::List getBuckets() const;
    bool empty() const { return _map.empty(); }
    void setBucketState(BucketId bucketId, bool active);
    void createBucket(BucketId bucketId);
    void deleteEmptyBucket(BucketId bucketId);
    BucketId::List getActiveBuckets() const;
    BucketId::List populateActiveBuckets(BucketId::List buckets);
    size_t size() const { return _map.size(); }
    bool isActiveBucket(BucketId bucketId) const;
    void unloadBucket(BucketId bucket, const BucketState &delta);
    size_t getNumActiveDocs() const { return _numActiveDocs.load(std::memory_order_relaxed); }

    // Avoid using this one as it breaks encapsulation
    BucketState *getBucketStatePtr(BucketId bucket);
    // Must be called if buckets state aquired with getBucketStatePtr has been modified.
    void restoreIntegrity();
    bool validateIntegrity() const;
};

}
