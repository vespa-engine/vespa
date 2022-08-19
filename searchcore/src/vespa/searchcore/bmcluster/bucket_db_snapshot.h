// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/persistence/spi/bucketinfo.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_set.h>

namespace storage::spi { struct PersistenceProvider; }

namespace search::bmcluster {

/*
 * Class representing a snapshot of bucket db below SPI for a single node and a single bucket space
 */
class BucketDbSnapshot
{
    using BucketInfoMap = vespalib::hash_map<document::BucketId, storage::spi::BucketInfo, document::BucketId::hash>;
    BucketInfoMap _buckets;
public:
    using BucketIdSet = vespalib::hash_set<document::BucketId, document::BucketId::hash>;
    BucketDbSnapshot();
    BucketDbSnapshot(const BucketDbSnapshot &) = delete;
    BucketDbSnapshot & operator=(const BucketDbSnapshot &) = delete;
    BucketDbSnapshot(BucketDbSnapshot &&) noexcept = default;
    BucketDbSnapshot & operator=(BucketDbSnapshot &&) noexcept = default;
    ~BucketDbSnapshot();
    void populate(document::BucketSpace bucket_space, storage::spi::PersistenceProvider& provider);
    uint32_t count_new_documents(const BucketDbSnapshot &old) const;
    void populate_bucket_id_set(BucketIdSet& buckets) const;
    const storage::spi::BucketInfo* try_get_bucket_info(document::BucketId bucket_id) const;
};

}
