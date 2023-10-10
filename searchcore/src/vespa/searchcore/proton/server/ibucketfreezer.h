// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>

namespace proton {

class IBucketFreezer
{
public:
    virtual ~IBucketFreezer() {}
    virtual void freezeBucket(document::BucketId bucket) = 0;
    virtual void thawBucket(document::BucketId bucket) = 0;
};


} // namespace proton

