// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document { class BucketId; }

namespace proton {

/**
 * Interface class used by a registered listener to get notifications about
 * bucket frozenness changes.
 */
class IBucketFreezeListener
{
public:
    virtual ~IBucketFreezeListener() {}

    /**
     * Signal that the given bucket has been thawed.
     * A thawed bucket can be considered for moving.
     */
    virtual void notifyThawedBucket(const document::BucketId &bucket) = 0;
};

} // namespace proton

