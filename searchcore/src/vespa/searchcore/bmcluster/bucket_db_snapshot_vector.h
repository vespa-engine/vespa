// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_db_snapshot.h"

namespace storage::lib { class ClusterStateBundle; }

namespace search::bmcluster {

/*
 * Class representing snapshots of bucket db below SPI for multiple nodes and bucket spaces.
 */
class BucketDbSnapshotVector
{
    vespalib::hash_map<document::BucketSpace, std::vector<BucketDbSnapshot>, document::BucketSpace::hash> _snapshots;
    using BucketIdSet = BucketDbSnapshot::BucketIdSet;
public:
    BucketDbSnapshotVector(const std::vector<storage::spi::PersistenceProvider *>& providers, const storage::lib::ClusterStateBundle &cluster_state_bundle);
    BucketDbSnapshotVector(const BucketDbSnapshotVector &) = delete;
    BucketDbSnapshotVector & operator = (const BucketDbSnapshotVector &) = delete;
    ~BucketDbSnapshotVector();
    uint32_t count_moved_documents(const BucketDbSnapshotVector &old) const;
    uint32_t count_lost_unique_documents(const BucketDbSnapshotVector &old) const;
};

}
