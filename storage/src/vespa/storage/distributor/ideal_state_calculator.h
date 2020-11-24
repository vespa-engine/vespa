// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketownership.h"
#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vector>
#include <unordered_map>

namespace storage::distributor {

class DistributorInterface;
class DistributorBucketSpaceRepo;

/*
 * Class for ideal state calculations with simple hash maps to reuse old
 * results when they are still valid.
 */
class IdealStateCalculator
{
    vespalib::hash_map<document::Bucket, BucketOwnership, document::Bucket::hash>             _ownerships;
    vespalib::hash_map<document::Bucket, std::vector<uint16_t>, document::Bucket::hash>       _ideal_nodes;
    std::unordered_map<document::BucketSpace, std::vector<bool>, document::BucketSpace::hash> _available_nodes;
    uint16_t                    _distribution_bits;
    const DistributorInterface& _distributor;
    DistributorBucketSpaceRepo& _bucket_space_repo;
    uint16_t                    _node_index;

    void clear();
    void enumerate_available_nodes();
    std::vector<uint16_t> get_ideal_nodes_fallback(const document::Bucket& bucket) const;

    BucketOwnership check_ownership_in_pending_and_given_state(const lib::Distribution& distribution, const lib::ClusterState& clusterState, const document::Bucket& bucket) const;

    BucketOwnership check_ownership_in_pending_and_current_state_fallback(const document::Bucket& bucket) const;
public:
    IdealStateCalculator(DistributorInterface& distributor, DistributorBucketSpaceRepo& bucket_space_repo);
    ~IdealStateCalculator();

    /**
     * Returns the ownership status of a bucket as decided with the given
     * distribution and cluster state -and- that of the pending cluster
     * state and distribution (if any pending exists).
     */
    BucketOwnership check_ownership_in_pending_and_current_state(const document::Bucket& bucket);

    /**
     * Returns the ideal nodes for the given bucket.
     */
    const std::vector<uint16_t>& get_ideal_nodes(const document::Bucket& bucket);

    const std::vector<bool>& get_available_nodes(document::BucketSpace bucket_space);

    void distribution_changed();
    void cluster_state_changed();
    void pending_cluster_state_changed() { cluster_state_changed(); }

};

}
