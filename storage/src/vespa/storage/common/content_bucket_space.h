// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketspace.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <mutex>

namespace storage {

namespace lib {
class ClusterState;
class Distribution;
}

struct ClusterStateAndDistribution {
    std::shared_ptr<const lib::ClusterState> _cluster_state;
    std::shared_ptr<const lib::Distribution> _distribution;

    ClusterStateAndDistribution() = default;
    ClusterStateAndDistribution(std::shared_ptr<const lib::ClusterState> cluster_state,
                                std::shared_ptr<const lib::Distribution> distribution) noexcept;
    ~ClusterStateAndDistribution();

    [[nodiscard]] bool valid() const noexcept { return _cluster_state && _distribution; }

    // Precondition: valid() == true
    [[nodiscard]] const lib::ClusterState& cluster_state() const noexcept { return *_cluster_state; }
    [[nodiscard]] const std::shared_ptr<const lib::ClusterState>& cluster_state_sp() const noexcept { return _cluster_state; }
    [[nodiscard]] const lib::Distribution& distribution() const noexcept { return  *_distribution; }
    [[nodiscard]] const std::shared_ptr<const lib::Distribution>& distribution_sp() const noexcept { return  _distribution; }

    [[nodiscard]] std::shared_ptr<const ClusterStateAndDistribution> with_new_state(
            std::shared_ptr<const lib::ClusterState> cluster_state) const;
    [[nodiscard]] std::shared_ptr<const ClusterStateAndDistribution> with_new_distribution(
            std::shared_ptr<const lib::Distribution> distribution) const;
};

/**
 * Class representing a bucket space (with associated bucket database) on a content node.
 */
class ContentBucketSpace {
private:
    document::BucketSpace _bucketSpace;
    StorBucketDatabase _bucketDatabase;
    mutable std::mutex _lock;
    std::shared_ptr<const ClusterStateAndDistribution> _state_and_distribution;
    bool _nodeUpInLastNodeStateSeenByProvider;
    bool _nodeMaintenanceInLastNodeStateSeenByProvider;

public:
    using UP = std::unique_ptr<ContentBucketSpace>;
    explicit ContentBucketSpace(document::BucketSpace bucketSpace, const ContentBucketDbOptions& db_opts);

    document::BucketSpace bucketSpace() const noexcept { return _bucketSpace; }
    StorBucketDatabase &bucketDatabase() { return _bucketDatabase; }

    void set_state_and_distribution(std::shared_ptr<const ClusterStateAndDistribution> state_and_distr) noexcept;
    [[nodiscard]] std::shared_ptr<const ClusterStateAndDistribution> state_and_distribution() const noexcept;

    bool getNodeUpInLastNodeStateSeenByProvider() const;
    void setNodeUpInLastNodeStateSeenByProvider(bool nodeUpInLastNodeStateSeenByProvider);
    bool getNodeMaintenanceInLastNodeStateSeenByProvider() const;
    void setNodeMaintenanceInLastNodeStateSeenByProvider(bool nodeMaintenanceInLastNodeStateSeenByProvider);
};

}
