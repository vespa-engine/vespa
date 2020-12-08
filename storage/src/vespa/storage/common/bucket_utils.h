// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/persistence/spi/bucket_limits.h>
#include <cassert>

namespace storage {

/**
 * Returns the super bucket key of the given bucket id key based on the minimum used bits allowed.
 *
 * For a bucket id that is explicit zero, the super bucket key is zero as well.
 */
inline uint64_t get_super_bucket_key(const document::BucketId& bucket_id) noexcept {
    if (bucket_id == document::BucketId(0)) {
        return 0;
    }
    assert(bucket_id.getUsedBits() >= spi::BucketLimits::MinUsedBits);
    // Since bucket keys have count-bits at the LSB positions, we want to look at the MSBs instead.
    return (bucket_id.toKey() >> (64 - spi::BucketLimits::MinUsedBits));
}

}
