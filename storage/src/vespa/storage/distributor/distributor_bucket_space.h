// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketownership.h"
#include "bucket_ownership_flags.h"
#include "ideal_service_layer_nodes_bundle.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <memory>
#include <vector>

namespace storage {
class BucketDatabase;
}

namespace storage::lib {
    class ClusterState;
    class Distribution;
}

namespace storage::distributor {

/**
 * A distributor bucket space holds specific state and information required for
 * keeping track of, and computing operations for, a single bucket space:
 *
 * Bucket database instance
 *   Each bucket space has its own entirely separate bucket database.
 * Distribution config
 *   Each bucket space _may_ operate with its own distribution config, in
 *   particular so that redundancy, ready copies etc can differ across
 *   bucket spaces.
 */
class DistributorBucketSpace {
    std::unique_ptr<BucketDatabase>  _bucketDatabase;
    std::shared_ptr<const lib::ClusterState> _clusterState;
    std::shared_ptr<const lib::Distribution> _distribution;
    uint16_t                                 _node_index;
    uint16_t                                 _distribution_bits;
    bool                                     _merges_inhibited;
    std::shared_ptr<const lib::ClusterState> _pending_cluster_state;
    std::vector<bool>                        _available_nodes;
    mutable vespalib::hash_map<document::BucketId, BucketOwnershipFlags, document::BucketId::hash>  _ownerships;
    mutable vespalib::hash_map<document::BucketId, IdealServiceLayerNodesBundle, document::BucketId::hash> _ideal_nodes;

    void clear();
    void enumerate_available_nodes();
    bool owns_bucket_in_state(const lib::Distribution& distribution, const lib::ClusterState& cluster_state, document::BucketId bucket) const;
public:
    explicit DistributorBucketSpace();
    explicit DistributorBucketSpace(uint16_t node_index);
    ~DistributorBucketSpace();

    DistributorBucketSpace(const DistributorBucketSpace&) = delete;
    DistributorBucketSpace& operator=(const DistributorBucketSpace&) = delete;
    DistributorBucketSpace(DistributorBucketSpace&&) = delete;
    DistributorBucketSpace& operator=(DistributorBucketSpace&&) = delete;

    BucketDatabase& getBucketDatabase() noexcept {
        assert(_bucketDatabase);
        return *_bucketDatabase;
    }
    const BucketDatabase& getBucketDatabase() const noexcept {
        assert(_bucketDatabase);
        return *_bucketDatabase;
    }

    void setClusterState(std::shared_ptr<const lib::ClusterState> clusterState);

    const lib::ClusterState &getClusterState() const noexcept { return *_clusterState; }
    const std::shared_ptr<const lib::ClusterState>& cluster_state_sp() const noexcept {
        return _clusterState;
    }

    void setDistribution(std::shared_ptr<const lib::Distribution> distribution);

    // Precondition: setDistribution has been called at least once prior.
    const lib::Distribution& getDistribution() const noexcept {
        return *_distribution;
    }
    const std::shared_ptr<const lib::Distribution>& distribution_sp() const noexcept {
        return _distribution;
    }

    void set_pending_cluster_state(std::shared_ptr<const lib::ClusterState> pending_cluster_state);
    bool has_pending_cluster_state() const noexcept { return static_cast<bool>(_pending_cluster_state); }
    const lib::ClusterState& get_pending_cluster_state() const noexcept { return *_pending_cluster_state; }

    void set_merges_inhibited(bool inhibited) noexcept {
        _merges_inhibited = inhibited;
    }
    [[nodiscard]] bool merges_inhibited() const noexcept {
        return _merges_inhibited;
    }

    /**
     * Returns true if this distributor owns the given bucket in the
     * given cluster and current distribution config.
     * Only used by unit tests.
     */
    bool owns_bucket_in_state(const lib::ClusterState& clusterState, document::BucketId bucket) const;

    const std::vector<bool>& get_available_nodes() const { return _available_nodes; }

    /**
     * Returns the ideal nodes bundle for the given bucket.
     */
    const IdealServiceLayerNodesBundle &get_ideal_service_layer_nodes_bundle(document::BucketId bucket) const;

    /*
     * Return bucket ownership flags for the given bucket. Bucket is always
     * considered owned in pending state if there is no pending state.
     */
    BucketOwnershipFlags get_bucket_ownership_flags(document::BucketId bucket) const;

    /**
     * Returns the ownership status of a bucket as decided with the current
     * distribution and cluster state -and- that of the pending cluster
     * state and distribution (if any pending exists).
     */
    BucketOwnership check_ownership_in_pending_and_current_state(document::BucketId bucket) const;
};

}
