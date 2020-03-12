#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/storageapi/defs.h>
#include <iosfwd>
#include <stddef.h>

namespace storage::distributor {

struct NewestReplica {
    api::Timestamp timestamp {0};
    document::BucketId bucket_id;
    uint16_t node {UINT16_MAX};

    static NewestReplica of(api::Timestamp timestamp,
                            const document::BucketId& bucket_id,
                            uint16_t node) noexcept {
        return {timestamp, bucket_id, node};
    }

    static NewestReplica make_empty() {
        return {api::Timestamp(0), document::BucketId(), 0};
    }

    bool operator==(const NewestReplica& rhs) const noexcept {
        return ((timestamp == rhs.timestamp) &&
                (bucket_id == rhs.bucket_id) &&
                (node == rhs.node));
    }
    bool operator!=(const NewestReplica& rhs) const noexcept {
        return !(*this == rhs);
    }
};


std::ostream& operator<<(std::ostream&, const NewestReplica&);

}
