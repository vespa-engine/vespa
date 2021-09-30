// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document { class BucketId; }

namespace proton::bucketdb {

class Guard;

/**
 * Interface class used by a registered listener to get notifications about
 * non-empty buckets created due to split/join operations.
 */
class IBucketCreateListener
{
public:
    virtual ~IBucketCreateListener() = default;

    /**
     * Signal that the given bucket has been created due to split/join
     * operation.
     */
    virtual void notifyCreateBucket(const Guard & guard, const document::BucketId &bucket) = 0;
};

}
