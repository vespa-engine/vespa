// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storage/bucketdb/bucketcopy.h>

namespace storage::distributor::dbtransition {

struct Entry {
    Entry(const document::BucketId& bid,
          const BucketCopy& copy_) noexcept
        : bucket_key(bid.toKey()),
          copy(copy_)
    {}

    uint64_t bucket_key;
    BucketCopy copy;

    document::BucketId bucket_id() const noexcept {
        return document::BucketId(document::BucketId::keyToBucketId(bucket_key));
    }

    bool operator<(const Entry& other) const noexcept {
        return bucket_key < other.bucket_key;
    }
};

}
