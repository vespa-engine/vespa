// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.distributor_bucket_space_repo");

using document::BucketSpace;

namespace storage::distributor {

DistributorBucketSpaceRepo::DistributorBucketSpaceRepo(uint16_t node_index)
    : _map()
{
    add(document::FixedBucketSpaces::default_space(), std::make_unique<DistributorBucketSpace>(node_index));
    add(document::FixedBucketSpaces::global_space(), std::make_unique<DistributorBucketSpace>(node_index));
}

DistributorBucketSpaceRepo::~DistributorBucketSpaceRepo() = default;

void
DistributorBucketSpaceRepo::add(document::BucketSpace bucketSpace, std::unique_ptr<DistributorBucketSpace> distributorBucketSpace)
{
    _map.emplace(bucketSpace, std::move(distributorBucketSpace));
}

DistributorBucketSpace &
DistributorBucketSpaceRepo::get(BucketSpace bucketSpace)
{
    auto itr = _map.find(bucketSpace);
    assert(itr != _map.end());
    return *itr->second;
}

const DistributorBucketSpace &
DistributorBucketSpaceRepo::get(BucketSpace bucketSpace) const
{
    auto itr = _map.find(bucketSpace);
    assert(itr != _map.end());
    return *itr->second;
}

void
DistributorBucketSpaceRepo::set_pending_cluster_state_bundle(const lib::ClusterStateBundle& cluster_state_bundle)
{
    for (auto& entry : _map) {
        entry.second->set_pending_cluster_state(cluster_state_bundle.getDerivedClusterState(entry.first));
    }
}

void
DistributorBucketSpaceRepo::clear_pending_cluster_state_bundle()
{
    for (auto& entry : _map) {
        entry.second->set_pending_cluster_state({});
    }
}

}
