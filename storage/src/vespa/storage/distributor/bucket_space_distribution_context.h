// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <cstdint>

namespace storage::lib {
    class Distribution;
    class ClusterState;
}
namespace document { class BucketId; }
namespace storage::distributor {

/**
 * Represents a consistent snapshot of cluster state and distribution config
 * information at a particular point in time. This is sufficient to compute
 * bucket ownership and distributions for the bucket space associated with
 * the context.
 *
 * Since this is a snapshot in time, the context is immutable once created.
 */
class BucketSpaceDistributionContext {
    std::shared_ptr<const lib::ClusterState> _active_cluster_state;
    std::shared_ptr<const lib::ClusterState> _default_active_cluster_state;
    std::shared_ptr<const lib::ClusterState> _pending_cluster_state; // May be null if no state is pending
    std::shared_ptr<const lib::Distribution> _distribution; // TODO ideally should have a pending distribution as well
    uint16_t _this_node_index;
public:
    BucketSpaceDistributionContext() = delete;
    // Public due to make_shared, prefer factory functions to instantiate instead.
    BucketSpaceDistributionContext(std::shared_ptr<const lib::ClusterState> active_cluster_state,
                                   std::shared_ptr<const lib::ClusterState> default_active_cluster_state,
                                   std::shared_ptr<const lib::ClusterState> pending_cluster_state,
                                   std::shared_ptr<const lib::Distribution> distribution,
                                   uint16_t this_node_index) noexcept;
    ~BucketSpaceDistributionContext();

    static std::shared_ptr<BucketSpaceDistributionContext> make_state_transition(
            std::shared_ptr<const lib::ClusterState> active_cluster_state,
            std::shared_ptr<const lib::ClusterState> default_active_cluster_state,
            std::shared_ptr<const lib::ClusterState> pending_cluster_state,
            std::shared_ptr<const lib::Distribution> distribution,
            uint16_t this_node_index);
    static std::shared_ptr<BucketSpaceDistributionContext> make_stable_state(
            std::shared_ptr<const lib::ClusterState> active_cluster_state,
            std::shared_ptr<const lib::ClusterState> default_active_cluster_state,
            std::shared_ptr<const lib::Distribution> distribution,
            uint16_t this_node_index);
    static std::shared_ptr<BucketSpaceDistributionContext> make_not_yet_initialized(uint16_t this_node_index);

    const std::shared_ptr<const lib::ClusterState>& active_cluster_state() const noexcept {
        return _active_cluster_state;
    }

    const std::shared_ptr<const lib::ClusterState>& default_active_cluster_state() const noexcept {
        return _default_active_cluster_state;
    }
    bool has_pending_state_transition() const noexcept {
        return (_pending_cluster_state.get() != nullptr);
    }
    // Returned shared_ptr is nullptr iff has_pending_state_transition() == false.
    const std::shared_ptr<const lib::ClusterState>& pending_cluster_state() const noexcept {
        return _pending_cluster_state;
    }

    bool bucket_owned_in_state(const lib::ClusterState& state, const document::BucketId& id) const;
    bool bucket_owned_in_active_state(const document::BucketId& id) const;
    bool bucket_owned_in_pending_state(const document::BucketId& id) const;

    uint16_t this_node_index() const noexcept { return _this_node_index; }
};

}
