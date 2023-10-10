// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/persistence/spi/bucket.h>

namespace proton {

/**
 * Helper class for creating bucket ids in order to support
 * persistence provider spi when getting feed from message bus.
 */
class BucketFactory {
public:
    static uint32_t getNumBucketBits() { return 8u; }
    static document::BucketId getBucketId(const document::DocumentId &docId);
    static storage::spi::Bucket getBucket(const document::DocumentId &docId);
};

} // namespace proton

