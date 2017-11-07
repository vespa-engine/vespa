// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/bucketdb/bucketcopy.h>

namespace storage::distributor::pendingbucketspacedbtransition {

struct Entry {
    Entry(const document::BucketId& bid,
          const BucketCopy& copy_)
        : bucketId(bid),
          copy(copy_)
    {}

    document::BucketId bucketId;
    BucketCopy copy;

    bool operator<(const Entry& other) const {
        return bucketId.toKey() < other.bucketId.toKey();
    }
};

}
