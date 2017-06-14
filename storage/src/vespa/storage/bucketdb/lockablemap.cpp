// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "lockablemap.hpp"
#include "storagebucketinfo.h"
#include "judymultimap.h"

namespace storage {

uint8_t
getMinDiffBits(uint16_t minBits, const document::BucketId& a, const document::BucketId& b) {
    for (uint32_t i = minBits; i <= std::min(a.getUsedBits(), b.getUsedBits()); i++) {
        document::BucketId a1(i, a.getRawId());
        document::BucketId b1(i, b.getRawId());
        if (b1.getId() != a1.getId()) {
            return i;
        }
    }
    return minBits;
}

bool
checkContains(document::BucketId::Type key, const document::BucketId& bucket,
              document::BucketId& result, document::BucketId::Type& keyResult)
{
    document::BucketId id = document::BucketId(document::BucketId::keyToBucketId(key));
    if (id.contains(bucket)) {
        result = id;
        keyResult = key;
        return true;
    }

    return false;
}

using bucketdb::StorageBucketInfo;

template class LockableMap<storage::JudyMultiMap<StorageBucketInfo, StorageBucketInfo, StorageBucketInfo, StorageBucketInfo> >;

}
