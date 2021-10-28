// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucket_space_distribution_context.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::distributor {

BucketSpaceDistributionContext::~BucketSpaceDistributionContext() = default;

BucketSpaceDistributionContext::BucketSpaceDistributionContext(
        std::shared_ptr<const lib::ClusterState> active_cluster_state,
        std::shared_ptr<const lib::ClusterState> default_active_cluster_state,
        std::shared_ptr<const lib::ClusterState> pending_cluster_state,
        std::shared_ptr<const lib::Distribution> distribution,
        uint16_t this_node_index) noexcept
    : _active_cluster_state(std::move(active_cluster_state)),
      _default_active_cluster_state(std::move(default_active_cluster_state)),
      _pending_cluster_state(std::move(pending_cluster_state)),
      _distribution(std::move(distribution)),
      _this_node_index(this_node_index)
{}

std::shared_ptr<BucketSpaceDistributionContext> BucketSpaceDistributionContext::make_state_transition(
        std::shared_ptr<const lib::ClusterState> active_cluster_state,
        std::shared_ptr<const lib::ClusterState> default_active_cluster_state,
        std::shared_ptr<const lib::ClusterState> pending_cluster_state,
        std::shared_ptr<const lib::Distribution> distribution,
        uint16_t this_node_index)
{
    return std::make_shared<BucketSpaceDistributionContext>(
            std::move(active_cluster_state), std::move(default_active_cluster_state),
            std::move(pending_cluster_state), std::move(distribution),
            this_node_index);
}

std::shared_ptr<BucketSpaceDistributionContext> BucketSpaceDistributionContext::make_stable_state(
        std::shared_ptr<const lib::ClusterState> active_cluster_state,
        std::shared_ptr<const lib::ClusterState> default_active_cluster_state,
        std::shared_ptr<const lib::Distribution> distribution,
        uint16_t this_node_index)
{
    return std::make_shared<BucketSpaceDistributionContext>(
            std::move(active_cluster_state), std::move(default_active_cluster_state),
            std::shared_ptr<const lib::ClusterState>(),
            std::move(distribution), this_node_index);
}

std::shared_ptr<BucketSpaceDistributionContext>
BucketSpaceDistributionContext::make_not_yet_initialized(uint16_t this_node_index)
{
    return std::make_shared<BucketSpaceDistributionContext>(
            std::make_shared<const lib::ClusterState>(),
            std::make_shared<const lib::ClusterState>(),
            std::shared_ptr<const lib::ClusterState>(),
            std::make_shared<const lib::Distribution>(),
            this_node_index);
}

bool BucketSpaceDistributionContext::bucket_owned_in_state(const lib::ClusterState& state,
                                                           const document::BucketId& id) const
{
    try {
        uint16_t owner_idx = _distribution->getIdealDistributorNode(state, id);
        return (owner_idx == _this_node_index);
    } catch (lib::TooFewBucketBitsInUseException&) {
        return false;
    } catch (lib::NoDistributorsAvailableException&) {
        return false;
    }
}

bool BucketSpaceDistributionContext::bucket_owned_in_active_state(const document::BucketId& id) const {
    return bucket_owned_in_state(*_active_cluster_state, id);
}

bool BucketSpaceDistributionContext::bucket_owned_in_pending_state(const document::BucketId& id) const {
    if (_pending_cluster_state) {
        return bucket_owned_in_state(*_pending_cluster_state, id);
    }
    return true; // No pending state, owned by default.
}

}
