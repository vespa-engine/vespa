// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space.h"
#include "bucketownership.h"
#include <vespa/storage/bucketdb/btree_bucket_database.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace storage::distributor {

namespace {

const char *up_states = "uri";
const char *nonretired_up_states = "ui";
const char *nonretired_or_maintenance_up_states = "uim";

}

DistributorBucketSpace::DistributorBucketSpace()
    : DistributorBucketSpace(0u)
{
}

DistributorBucketSpace::DistributorBucketSpace(uint16_t node_index)
    : _bucketDatabase(std::make_unique<BTreeBucketDatabase>()),
      _clusterState(),
      _distribution(),
      _node_index(node_index),
      _distribution_bits(1u),
      _merges_inhibited(false),
      _pending_cluster_state(),
      _available_nodes(),
      _ownerships(),
      _ideal_nodes()
{
}

DistributorBucketSpace::~DistributorBucketSpace() = default;

void
DistributorBucketSpace::clear()
{
    _ownerships.clear();
    _ideal_nodes.clear();
}

void
DistributorBucketSpace::enumerate_available_nodes()
{
    _distribution_bits = _clusterState->getDistributionBitCount();
    auto node_count = _clusterState->getNodeCount(lib::NodeType::STORAGE);
    if (_pending_cluster_state) {
        _distribution_bits = std::max(_distribution_bits, _pending_cluster_state->getDistributionBitCount());
        node_count = std::min(node_count, _pending_cluster_state->getNodeCount(lib::NodeType::STORAGE));
    }
    std::vector<bool> nodes(node_count);
    for (uint32_t i = 0; i < node_count; ++i) {
        lib::Node node_key(lib::NodeType::STORAGE, i);
        const lib::NodeState& ns(_clusterState->getNodeState(node_key));
        if (ns.getState().oneOf(up_states)) {
            if (!_pending_cluster_state || _pending_cluster_state->getNodeState(node_key).getState().oneOf(up_states)) {
                nodes[i] = true;
            }
        }
    }
    _available_nodes = std::move(nodes);
}

void
DistributorBucketSpace::setClusterState(std::shared_ptr<const lib::ClusterState> clusterState)
{
    _clusterState = std::move(clusterState);
    clear();
    enumerate_available_nodes();
}


void
DistributorBucketSpace::setDistribution(std::shared_ptr<const lib::Distribution> distribution) {
    _distribution = std::move(distribution);
    clear();
}

void
DistributorBucketSpace::set_pending_cluster_state(std::shared_ptr<const lib::ClusterState> pending_cluster_state)
{
    _pending_cluster_state = std::move(pending_cluster_state);
    clear();
    enumerate_available_nodes();
}

bool
DistributorBucketSpace::owns_bucket_in_state(
        const lib::Distribution& distribution,
        const lib::ClusterState& cluster_state,
        document::BucketId bucket) const
{
    try {
        uint16_t distributor = distribution.getIdealDistributorNode(cluster_state, bucket);

        return (_node_index == distributor);
    } catch (lib::TooFewBucketBitsInUseException& e) {
        return false;
    } catch (lib::NoDistributorsAvailableException& e) {
        return false;
    }
}

bool
DistributorBucketSpace::owns_bucket_in_state(
        const lib::ClusterState& clusterState,
        document::BucketId bucket) const
{
    return owns_bucket_in_state(*_distribution, clusterState, bucket);
}

namespace {

void
setup_ideal_nodes_bundle(IdealServiceLayerNodesBundle& ideal_nodes_bundle,
                         const lib::Distribution& distribution,
                         const lib::ClusterState& cluster_state,
                         document::BucketId bucket)
{
    ideal_nodes_bundle.set_available_nodes(distribution.getIdealStorageNodes(cluster_state, bucket, up_states));
    ideal_nodes_bundle.set_available_nonretired_nodes(distribution.getIdealStorageNodes(cluster_state, bucket, nonretired_up_states));
    ideal_nodes_bundle.set_available_nonretired_or_maintenance_nodes(distribution.getIdealStorageNodes(cluster_state, bucket, nonretired_or_maintenance_up_states));
}

/*
 * Check if we trigger a streaming search latency optimization where
 * we spread out data for a single group over multiple storage nodes.
 * See storage::lib::Distribution::getStorageSeed for details.
 */
bool is_split_group_bucket(document::BucketId bucket) noexcept
{
    return bucket.getUsedBits() > 33;
}

// Ideal service layer nodes bundle used when is_split_group_bucket returns true
thread_local IdealServiceLayerNodesBundle fallback_ideal_nodes_bundle;

}

const IdealServiceLayerNodesBundle&
DistributorBucketSpace::get_ideal_service_layer_nodes_bundle(document::BucketId bucket) const
{
    assert(bucket.getUsedBits() >= _distribution_bits);
    if (is_split_group_bucket(bucket)) {
        IdealServiceLayerNodesBundle &ideal_nodes_bundle = fallback_ideal_nodes_bundle;
        setup_ideal_nodes_bundle(ideal_nodes_bundle, *_distribution, *_clusterState, bucket);
        return ideal_nodes_bundle;
    }
    document::BucketId lookup_bucket(is_split_group_bucket(bucket) ? bucket.getUsedBits() : _distribution_bits, bucket.getId());
    auto itr = _ideal_nodes.find(lookup_bucket);
    if (itr != _ideal_nodes.end()) {
        return itr->second;
    }
    IdealServiceLayerNodesBundle ideal_nodes_bundle;
    setup_ideal_nodes_bundle(ideal_nodes_bundle, *_distribution, *_clusterState, lookup_bucket);
    auto insres = _ideal_nodes.insert(std::make_pair(lookup_bucket, std::move(ideal_nodes_bundle)));
    assert(insres.second);
    return insres.first->second;
}

BucketOwnershipFlags
DistributorBucketSpace::get_bucket_ownership_flags(document::BucketId bucket) const
{
    if (bucket.getUsedBits() < _distribution_bits) {
        BucketOwnershipFlags flags;
        if (!_pending_cluster_state) {
            flags.set_owned_in_pending_state();
        }
        return flags;
    }
    document::BucketId super_bucket(_distribution_bits, bucket.getId());
    auto itr = _ownerships.find(super_bucket);
    if (itr != _ownerships.end()) {
        return itr->second;
    }
    BucketOwnershipFlags flags;
    if (!_pending_cluster_state || owns_bucket_in_state(*_distribution, *_pending_cluster_state, super_bucket)) {
        flags.set_owned_in_pending_state();
    }
    if (owns_bucket_in_state(*_distribution, *_clusterState, super_bucket)) {
        flags.set_owned_in_current_state();
    }
    auto insres = _ownerships.insert(std::make_pair(super_bucket, flags));
    assert(insres.second);
    return insres.first->second;
}

BucketOwnership
DistributorBucketSpace::check_ownership_in_pending_and_current_state(document::BucketId bucket) const
{
    auto flags = get_bucket_ownership_flags(bucket);
    if (!flags.owned_in_pending_state()) {
        assert(_pending_cluster_state);
        return BucketOwnership::createNotOwnedInState(*_pending_cluster_state);
    }
    if (flags.owned_in_current_state()) {
        return BucketOwnership::createOwned();
    } else {
        return BucketOwnership::createNotOwnedInState(*_clusterState);
    }
}

}
