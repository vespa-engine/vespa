// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/persistence/spi/types.h>


namespace proton::bucketdb {

/*
 * Class containing meta data for a single document being removed from
 * bucket db.
 */
class RemoveBatchEntry {
    document::GlobalId      _gid;
    document::BucketId      _bucket_id;
    storage::spi::Timestamp _timestamp;
    uint32_t                _doc_size;
public:
    RemoveBatchEntry(const document::GlobalId& gid, const document::BucketId& bucket_id, const storage::spi::Timestamp& timestamp, uint32_t doc_size) noexcept
        : _gid(gid),
          _bucket_id(bucket_id),
          _timestamp(timestamp),
          _doc_size(doc_size)
    {
    }

    const document::GlobalId& get_gid() const noexcept { return _gid; }
    const document::BucketId& get_bucket_id() const noexcept { return _bucket_id; }
    const storage::spi::Timestamp& get_timestamp() const noexcept { return _timestamp; }
    uint32_t get_doc_size() const noexcept { return _doc_size; }
};

}
