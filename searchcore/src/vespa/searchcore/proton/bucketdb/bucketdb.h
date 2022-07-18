// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketstate.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/persistence/spi/result.h>
#include <map>

namespace proton::bucketdb { class RemoveBatchEntry; }

namespace proton {

class BucketDB
{
public:
    typedef document::GlobalId GlobalId;
    typedef document::BucketId BucketId;
    typedef storage::spi::Timestamp Timestamp;
    typedef storage::spi::BucketChecksum BucketChecksum;
    typedef bucketdb::BucketState BucketState;
    typedef std::map<BucketId, BucketState> Map;

private:
    Map _map;
    BucketId _cachedBucketId;
    BucketState _cachedBucketState;

    void clear();
    void checkEmpty() const;
public:
    BucketDB();
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
    bool empty() const;
    void setBucketState(BucketId bucketId, bool active);
    void createBucket(BucketId bucketId);
    void deleteEmptyBucket(BucketId bucketId);
    BucketId::List getActiveBuckets() const;
    BucketId::List populateActiveBuckets(BucketId::List buckets);
    size_t size() const { return _map.size(); }
    bool isActiveBucket(BucketId bucketId) const;
    BucketState *getBucketStatePtr(BucketId bucket);
    void unloadBucket(BucketId bucket, const BucketState &delta);
};

}

