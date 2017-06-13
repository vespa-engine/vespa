// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage {

namespace distributor {

/**
   Merges two sorted lists of buckets.

   Creates two lists:
   - One list containing buckets missing from the old list, or that are in both and have different checksums (to get updated bucket information)
   - One list containing buckets missing from the new list (to be deleted).
*/
class BucketListMerger
{
public:
    typedef std::pair<document::BucketId, api::BucketInfo> BucketEntry;
    typedef std::vector<BucketEntry> BucketList;

    BucketListMerger(const BucketList& newList, const BucketList& oldList,
                     uint64_t timestamp);

    const std::vector<BucketEntry>& getAddedEntries()
    { return _addedEntries; }

    const std::vector<document::BucketId>& getRemovedEntries()
    { return _removedEntries; }

    uint64_t getTimestamp() const { return _timestamp; }

private:
    std::vector<BucketEntry> _addedEntries;
    std::vector<document::BucketId> _removedEntries;
    uint64_t _timestamp;
};

}

}

