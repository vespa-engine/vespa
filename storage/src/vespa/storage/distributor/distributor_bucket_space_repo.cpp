// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.distributor_bucket_space_repo");

using document::BucketSpace;
using document::FixedBucketSpaces;

namespace storage::distributor {

DistributorBucketSpaceRepo::DistributorBucketSpaceRepo(uint16_t node_index)
    : _map()
{
    add(FixedBucketSpaces::default_space(), std::make_unique<DistributorBucketSpace>(node_index));
    add(FixedBucketSpaces::global_space(), std::make_unique<DistributorBucketSpace>(node_index));
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

namespace {

bool content_node_is_up(const lib::ClusterState& state, uint16_t index) noexcept {
    return (state.getNodeState(lib::Node(lib::NodeType::STORAGE, index)).getState() == lib::State::UP);
}

bool content_node_is_in_maintenance(const lib::ClusterState& state, uint16_t index) noexcept {
    return (state.getNodeState(lib::Node(lib::NodeType::STORAGE, index)).getState() == lib::State::MAINTENANCE);
}

// Prioritized global bucket merging is taking place if at least one content node is
// marked as Up in the global bucket space state, but Maintenance in the default
// bucket space state.
bool bundle_implies_global_merging_active(const lib::ClusterStateBundle& bundle) noexcept {
    auto& default_cs = bundle.getDerivedClusterState(FixedBucketSpaces::default_space());
    auto& global_cs  = bundle.getDerivedClusterState(FixedBucketSpaces::global_space());
    if (default_cs.get() == global_cs.get()) {
        return false;
    }
    uint16_t node_count = global_cs->getNodeCount(lib::NodeType::STORAGE);
    for (uint16_t i = 0; i < node_count; ++i) {
        if (content_node_is_up(*global_cs, i) && content_node_is_in_maintenance(*default_cs, i)) {
            return true;
        }
    }
    return false;
}

}

void
DistributorBucketSpaceRepo::enable_cluster_state_bundle(const lib::ClusterStateBundle& cluster_state_bundle)
{
    for (auto& entry : _map) {
        entry.second->setClusterState(cluster_state_bundle.getDerivedClusterState(entry.first));
    }
    get(FixedBucketSpaces::default_space()).set_merges_inhibited(
            bundle_implies_global_merging_active(cluster_state_bundle));
}

void
DistributorBucketSpaceRepo::set_pending_cluster_state_bundle(const lib::ClusterStateBundle& cluster_state_bundle)
{
    for (auto& entry : _map) {
        entry.second->set_pending_cluster_state(cluster_state_bundle.getDerivedClusterState(entry.first));
    }
    get(FixedBucketSpaces::default_space()).set_merges_inhibited(
            bundle_implies_global_merging_active(cluster_state_bundle));
}

void
DistributorBucketSpaceRepo::clear_pending_cluster_state_bundle()
{
    for (auto& entry : _map) {
        entry.second->set_pending_cluster_state({});
    }
}

}
