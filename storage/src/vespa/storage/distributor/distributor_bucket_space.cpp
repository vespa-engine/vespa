// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space.h"
#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <vespa/storage/bucketdb/btree_bucket_database.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>

namespace storage::distributor {

namespace {

std::unique_ptr<BucketDatabase> make_default_db_impl(bool use_btree_db) {
    if (use_btree_db) {
        return std::make_unique<BTreeBucketDatabase>();
    }
    return std::make_unique<MapBucketDatabase>();
}

}

DistributorBucketSpace::DistributorBucketSpace(bool use_btree_db)
    : _bucketDatabase(make_default_db_impl(use_btree_db)),
      _clusterState(),
      _distribution()
{
}

DistributorBucketSpace::~DistributorBucketSpace() = default;

void
DistributorBucketSpace::setClusterState(std::shared_ptr<const lib::ClusterState> clusterState)
{
    _clusterState = std::move(clusterState);
}


void
DistributorBucketSpace::setDistribution(std::shared_ptr<const lib::Distribution> distribution) {
    _distribution = std::move(distribution);
}

}
