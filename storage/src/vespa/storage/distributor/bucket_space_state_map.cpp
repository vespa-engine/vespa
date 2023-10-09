// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_space_state_map.h"
#include <vespa/document/bucket/fixed_bucket_spaces.h>

using document::BucketSpace;

namespace storage::distributor {

BucketSpaceState::BucketSpaceState()
    : _cluster_state(),
      _distribution()
{
}

void
BucketSpaceState::set_cluster_state(std::shared_ptr<const lib::ClusterState> cluster_state)
{
    _cluster_state = std::move(cluster_state);
}

void
BucketSpaceState::set_distribution(std::shared_ptr<const lib::Distribution> distribution)
{
    _distribution = distribution;
}

BucketSpaceStateMap::BucketSpaceStateMap()
    : _map()
{
    _map.emplace(document::FixedBucketSpaces::default_space(), std::make_unique<BucketSpaceState>());
    _map.emplace(document::FixedBucketSpaces::global_space(), std::make_unique<BucketSpaceState>());
}

const BucketSpaceState&
BucketSpaceStateMap::get(document::BucketSpace space) const
{
    auto itr = _map.find(space);
    assert(itr != _map.end());
    return *itr->second;
}

BucketSpaceState&
BucketSpaceStateMap::get(document::BucketSpace space)
{
    auto itr = _map.find(space);
    assert(itr != _map.end());
    return *itr->second;
}

void
BucketSpaceStateMap::set_cluster_state(std::shared_ptr<const lib::ClusterState> cluster_state)
{
    for (auto& space : _map) {
        space.second->set_cluster_state(cluster_state);
    }
}

void
BucketSpaceStateMap::set_distribution(std::shared_ptr<const lib::Distribution> distribution)
{
    for (auto& space : _map) {
        space.second->set_distribution(distribution);
    }
}

const lib::ClusterState&
BucketSpaceStateMap::get_cluster_state(document::BucketSpace space) const
{
    auto itr = _map.find(space);
    assert(itr != _map.end());
    return itr->second->get_cluster_state();
}

const lib::Distribution&
BucketSpaceStateMap::get_distribution(document::BucketSpace space) const
{
    auto itr = _map.find(space);
    assert(itr != _map.end());
    return itr->second->get_distribution();
}

}
