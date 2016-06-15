// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <memory>
#include <vespa/searchcore/proton/server/ibucketfreezer.h>

namespace proton {

class BucketGuard {
    document::BucketId _bucket;
    IBucketFreezer    &_freezer;

public:
    typedef std::unique_ptr<BucketGuard> UP;
    BucketGuard(const BucketGuard &) = delete;
    BucketGuard & operator=(const BucketGuard &) = delete;
    BucketGuard(BucketGuard &&) = delete;
    BucketGuard & operator=(BucketGuard &&) = delete;

    BucketGuard(document::BucketId bucket, IBucketFreezer &freezer)
        : _bucket(bucket),
          _freezer(freezer)
    {
        freezer.freezeBucket(bucket);
    }

    ~BucketGuard() {
        _freezer.thawBucket(_bucket);
    }
};

}  // namespace proton

