// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <atomic>

namespace storage {

/**
 * Utility class for keeping track of the lowest used bits count seen
 * across a set of buckets.
 *
 * Thread safe for reads and writes.
 */
class MinimumUsedBitsTracker
{
    std::atomic<uint32_t> _min_used_bits;
public:
    constexpr MinimumUsedBitsTracker() noexcept
        : _min_used_bits(58)
    {}

    /**
     * Returns true iff new bucket led to a decrease in the used bits count.
     */
    bool update(const document::BucketId& bucket) noexcept {
        const uint32_t bucket_bits = bucket.getUsedBits();
        uint32_t current_bits = _min_used_bits.load(std::memory_order_relaxed);
        if (bucket_bits < current_bits) {
            while (!_min_used_bits.compare_exchange_strong(current_bits, bucket_bits,
                                                           std::memory_order_relaxed,
                                                           std::memory_order_relaxed))
            {
                if (bucket_bits >= current_bits) {
                    return false; // We've raced with another writer that had lower or equal bits to our own bucket.
                }
            }
            return true;
        }
        return false;
    }

    [[nodiscard]] uint32_t getMinUsedBits() const noexcept {
        return _min_used_bits.load(std::memory_order_relaxed);
    }

    void setMinUsedBits(uint32_t minUsedBits) noexcept {
        _min_used_bits.store(minUsedBits, std::memory_order_relaxed);
    }
};

}
