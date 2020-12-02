// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distributor_bucket_space.h"
#include "bucketownership.h"
#include <vespa/storage/bucketdb/btree_bucket_database.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

namespace storage::distributor {

namespace {

const char *up_states = "uri";

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
        _distribution_bits = std::min(_distribution_bits, _pending_cluster_state->getDistributionBitCount());
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

std::vector<uint16_t>
DistributorBucketSpace::get_ideal_nodes_fallback(document::BucketId bucket) const
{
    return _distribution->getIdealStorageNodes(*_clusterState, bucket, up_states);
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

bool
DistributorBucketSpace::owns_bucket_in_current_state(document::BucketId bucket) const
{
    return owns_bucket_in_state(*_distribution, *_clusterState, bucket);
}

BucketOwnership
DistributorBucketSpace::check_ownership_in_pending_state(document::BucketId bucket) const
{
    if (_pending_cluster_state) {
        if (!owns_bucket_in_state(*_pending_cluster_state, bucket)) {
            return BucketOwnership::createNotOwnedInState(*_pending_cluster_state);
        }
    }
    return BucketOwnership::createOwned();
}
BucketOwnership
DistributorBucketSpace::check_ownership_in_pending_and_given_state(
        const lib::Distribution& distribution,
        const lib::ClusterState& clusterState,
        document::BucketId bucket) const
{
    try {
        BucketOwnership pendingRes(
                check_ownership_in_pending_state(bucket));
        if (!pendingRes.isOwned()) {
            return pendingRes;
        }
        uint16_t distributor = distribution.getIdealDistributorNode(
                clusterState, bucket);

        if (_node_index == distributor) {
            return BucketOwnership::createOwned();
        } else {
            return BucketOwnership::createNotOwnedInState(clusterState);
        }
    } catch (lib::TooFewBucketBitsInUseException& e) {
        return BucketOwnership::createNotOwnedInState(clusterState);
    } catch (lib::NoDistributorsAvailableException& e) {
        return BucketOwnership::createNotOwnedInState(clusterState);
    }
}

BucketOwnership
DistributorBucketSpace::check_ownership_in_pending_and_current_state_fallback(document::BucketId bucket) const
{
    return check_ownership_in_pending_and_given_state(*_distribution, *_clusterState, bucket);
}

std::vector<uint16_t>
DistributorBucketSpace::get_ideal_nodes(document::BucketId bucket) const
{
    assert(bucket.getUsedBits() >= _distribution_bits);
    if (bucket.getUsedBits() > 33) { // cf. storage::lib::Distribution::getStorageSeed
        // Cannot map to super bucket ==> cannot cache result
        return get_ideal_nodes_fallback(bucket);
    }
    document::BucketId super_bucket(_distribution_bits, bucket.getId());
    auto itr = _ideal_nodes.find(super_bucket);
    if (itr != _ideal_nodes.end()) {
        return itr->second;
    }
    auto insres = _ideal_nodes.insert(std::make_pair(super_bucket, get_ideal_nodes_fallback(super_bucket)));
    assert(insres.second);
    return insres.first->second;
}

BucketOwnership
DistributorBucketSpace::check_ownership_in_pending_and_current_state(document::BucketId bucket) const
{
    if (bucket.getUsedBits() < _distribution_bits) {
        // Cannot map to super bucket ==> cannot cache result
        return check_ownership_in_pending_and_current_state_fallback(bucket);
    }
    document::BucketId super_bucket(_distribution_bits, bucket.getId());
    auto itr = _ownerships.find(super_bucket);
    if (itr != _ownerships.end()) {
        return itr->second;
    }
    auto insres = _ownerships.insert(std::make_pair(super_bucket, check_ownership_in_pending_and_current_state_fallback(super_bucket)));
    assert(insres.second);
    return insres.first->second;
}

}
