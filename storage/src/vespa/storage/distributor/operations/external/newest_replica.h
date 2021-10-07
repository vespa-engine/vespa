// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storageapi/defs.h>
#include <iosfwd>
#include <stddef.h>

namespace storage::distributor {

/*
 * Tracks the information required to identify the location of the newest replica
 * for any given document. Newest here means the replica containing the document
 * version with the highest mutation timestamp.
 */
struct NewestReplica {
    api::Timestamp timestamp {0};
    document::BucketId bucket_id;
    uint16_t node {UINT16_MAX};
    bool is_tombstone {false};

    static NewestReplica of(api::Timestamp timestamp,
                            const document::BucketId& bucket_id,
                            uint16_t node,
                            bool is_tombstone) noexcept {
        return {timestamp, bucket_id, node, is_tombstone};
    }

    static NewestReplica make_empty() {
        return {api::Timestamp(0), document::BucketId(), 0, false};
    }

    bool operator==(const NewestReplica& rhs) const noexcept {
        return ((timestamp == rhs.timestamp) &&
                (bucket_id == rhs.bucket_id) &&
                (node == rhs.node) &&
                (is_tombstone == rhs.is_tombstone));
    }
    bool operator!=(const NewestReplica& rhs) const noexcept {
        return !(*this == rhs);
    }
};


std::ostream& operator<<(std::ostream&, const NewestReplica&);

}
