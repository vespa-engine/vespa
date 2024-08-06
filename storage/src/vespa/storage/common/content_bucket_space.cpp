// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "content_bucket_space.h"

namespace storage {

ClusterStateAndDistribution::ClusterStateAndDistribution(
        std::shared_ptr<const lib::ClusterState> cluster_state,
        std::shared_ptr<const lib::Distribution> distribution) noexcept
    : _cluster_state(std::move(cluster_state)),
      _distribution(std::move(distribution))
{
    assert(_cluster_state && _distribution);
}

ClusterStateAndDistribution::~ClusterStateAndDistribution() = default;

std::shared_ptr<const ClusterStateAndDistribution>
ClusterStateAndDistribution::with_new_state(std::shared_ptr<const lib::ClusterState> cluster_state) const {
    return std::make_shared<const ClusterStateAndDistribution>(std::move(cluster_state), _distribution);
}

std::shared_ptr<const ClusterStateAndDistribution>
ClusterStateAndDistribution::with_new_distribution(std::shared_ptr<const lib::Distribution> distribution) const {
    return std::make_shared<const ClusterStateAndDistribution>(_cluster_state, std::move(distribution));
}

ContentBucketSpace::ContentBucketSpace(document::BucketSpace bucketSpace,
                                       const ContentBucketDbOptions& db_opts)
    : _bucketSpace(bucketSpace),
      _bucketDatabase(db_opts),
      _lock(),
      _state_and_distribution(std::make_shared<ClusterStateAndDistribution>()),
      _nodeUpInLastNodeStateSeenByProvider(false),
      _nodeMaintenanceInLastNodeStateSeenByProvider(false)
{
}

void
ContentBucketSpace::set_state_and_distribution(std::shared_ptr<const ClusterStateAndDistribution> state_and_distr) noexcept {
    assert(state_and_distr);
    std::lock_guard guard(_lock);
    _state_and_distribution = std::move(state_and_distr);
}

std::shared_ptr<const ClusterStateAndDistribution>
ContentBucketSpace::state_and_distribution() const noexcept {
    std::lock_guard guard(_lock);
    return _state_and_distribution;
}

bool
ContentBucketSpace::getNodeUpInLastNodeStateSeenByProvider() const
{
    std::lock_guard guard(_lock);
    return _nodeUpInLastNodeStateSeenByProvider;
}

void
ContentBucketSpace::setNodeUpInLastNodeStateSeenByProvider(bool nodeUpInLastNodeStateSeenByProvider)
{
    std::lock_guard guard(_lock);
    _nodeUpInLastNodeStateSeenByProvider = nodeUpInLastNodeStateSeenByProvider;
}

bool
ContentBucketSpace::getNodeMaintenanceInLastNodeStateSeenByProvider() const
{
    std::lock_guard guard(_lock);
    return _nodeMaintenanceInLastNodeStateSeenByProvider;
}

void
ContentBucketSpace::setNodeMaintenanceInLastNodeStateSeenByProvider(bool nodeMaintenanceInLastNodeStateSeenByProvider)
{
    std::lock_guard guard(_lock);
    _nodeMaintenanceInLastNodeStateSeenByProvider = nodeMaintenanceInLastNodeStateSeenByProvider;
}

}
