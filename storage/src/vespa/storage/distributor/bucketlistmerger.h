// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vector>

namespace storage::distributor {

/**
   Merges two sorted lists of buckets.

   Creates two lists:
   - One list containing buckets missing from the old list, or that are in both and have different checksums (to get updated bucket information)
   - One list containing buckets missing from the new list (to be deleted).
*/
class BucketListMerger
{
public:
    using BucketEntry = std::pair<document::BucketId, api::BucketInfo>;
    using BucketList = std::vector<BucketEntry>;

    BucketListMerger(const BucketList& newList, const BucketList& oldList, uint64_t timestamp);

    const std::vector<BucketEntry>& getAddedEntries() const { return _addedEntries; }
    const std::vector<document::BucketId>& getRemovedEntries() const { return _removedEntries; }
    uint64_t getTimestamp() const { return _timestamp; }
private:
    std::vector<BucketEntry>        _addedEntries;
    std::vector<document::BucketId> _removedEntries;
    uint64_t                        _timestamp;
};

}
