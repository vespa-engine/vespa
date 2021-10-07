// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <algorithm>
#include <vespa/document/bucket/bucketid.h>

namespace storage {

/**
 * Utility class for keeping track of the lowest used bits count seen
 * across a set of buckets.
 *
 * Not threadsafe by itself.
 */
class MinimumUsedBitsTracker
{
    uint32_t _minUsedBits;
public:
    MinimumUsedBitsTracker()
        : _minUsedBits(58)
    {}

    /**
     * Returns true if new bucket led to a decrease in the used bits count.
     */
    bool update(const document::BucketId& bucket) {
        if (bucket.getUsedBits() < _minUsedBits) {
            _minUsedBits = bucket.getUsedBits();
            return true;
        }
        return false;
    }

    uint32_t getMinUsedBits() const {
        return _minUsedBits;
    }

    void setMinUsedBits(uint32_t minUsedBits) {
        _minUsedBits = minUsedBits;
    }
};

}
