// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document { class BucketId; }

namespace proton::bucketdb {

class IBucketCreateListener;
class Guard;

/**
 * Interface class used to (un)register a listener to get notifications about
 * non-empty buckets created due to split/join operations.
 */
class IBucketCreateNotifier
{
public:
    virtual ~IBucketCreateNotifier() = default;

    /**
     * Signal that the given bucket has been created due to split/join
     * operation.
     */
    virtual void notifyCreateBucket(const Guard & guard, const document::BucketId &bucket) = 0;

    /*
     * Register bucket create listener.
     */
    virtual void addListener(IBucketCreateListener *listener) = 0;

    /*
     * Unregister bucket create listener.
     */
    virtual void removeListener(IBucketCreateListener *listener) = 0;
};

}
