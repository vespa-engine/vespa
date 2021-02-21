// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <memory>

namespace proton {

class IBucketFreezeListener;

class IFrozenBucketHandler
{
public:
    class ExclusiveBucketGuard {
    public:
        typedef std::unique_ptr<ExclusiveBucketGuard> UP;
        ExclusiveBucketGuard(document::BucketId bucketId) : _bucketId(bucketId) { }
        virtual ~ExclusiveBucketGuard() { }
        document::BucketId getBucket() const { return _bucketId; }
    private:
        document::BucketId _bucketId;
    };

    virtual ~IFrozenBucketHandler() = default;
    virtual ExclusiveBucketGuard::UP acquireExclusiveBucket(document::BucketId bucket) = 0;
    virtual void addListener(IBucketFreezeListener *listener) = 0;
    virtual void removeListener(IBucketFreezeListener *listener) = 0;
};

}
