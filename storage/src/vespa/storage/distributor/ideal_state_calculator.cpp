// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ideal_state_calculator.h"
#include "distributor_bucket_space_repo.h"
#include "distributor_bucket_space.h"
#include "distributorinterface.h"
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vdslib/distribution/distribution.h>

using document::Bucket;

namespace storage::distributor {

namespace {

std::vector<bool> no_available_nodes;

}

IdealStateCalculator::IdealStateCalculator(DistributorInterface& distributor, DistributorBucketSpaceRepo& bucket_space_repo)
    : _ownerships(),
      _ideal_nodes(),
      _available_nodes(),
      _distribution_bits(0u),
      _distributor(distributor),
      _bucket_space_repo(bucket_space_repo),
      _node_index(_distributor.getDistributorIndex())
{
}

IdealStateCalculator::~IdealStateCalculator() = default;

BucketOwnership
IdealStateCalculator::check_ownership_in_pending_and_current_state(const Bucket& bucket)
{
    if (bucket.getBucketId().getUsedBits() < _distribution_bits) {
        // Cannot map to superbucket ==> cannot cache result
        return check_ownership_in_pending_and_current_state_fallback(bucket);
    }
    Bucket super_bucket(bucket.getBucketSpace(), document::BucketId(_distribution_bits, bucket.getBucketId().getId()));
    auto itr = _ownerships.find(super_bucket);
    if (itr != _ownerships.end()) {
        return itr->second;
    }
    auto insres = _ownerships.insert(std::make_pair(super_bucket, check_ownership_in_pending_and_current_state_fallback(super_bucket)));
    assert(insres.second);
    return insres.first->second;
}

const std::vector<uint16_t>&
IdealStateCalculator::get_ideal_nodes(const Bucket& bucket)
{
    assert(bucket.getBucketId().getUsedBits() >= _distribution_bits);
    Bucket super_bucket(bucket.getBucketSpace(), document::BucketId(_distribution_bits, bucket.getBucketId().getId()));
    auto itr = _ideal_nodes.find(super_bucket);
    if (itr != _ideal_nodes.end()) {
        return itr->second;
    }
    auto insres = _ideal_nodes.insert(std::make_pair(super_bucket, get_ideal_nodes_fallback(super_bucket)));
    assert(insres.second);
    return insres.first->second;
}

void
IdealStateCalculator::clear()
{
    _ownerships.clear();
    _ideal_nodes.clear();
}

void
IdealStateCalculator::distribution_changed()
{
    clear();
}

void
IdealStateCalculator::cluster_state_changed()
{
    clear();
    enumerate_available_nodes();
}

std::vector<uint16_t>
IdealStateCalculator::get_ideal_nodes_fallback(const Bucket &bucket) const
{
    auto &bucket_space(_bucket_space_repo.get(bucket.getBucketSpace()));
    return bucket_space.getDistribution().getIdealStorageNodes(
            bucket_space.getClusterState(),
            bucket.getBucketId(),
            _distributor.getStorageNodeUpStates());
}

BucketOwnership
IdealStateCalculator::check_ownership_in_pending_and_given_state(
        const lib::Distribution& distribution,
        const lib::ClusterState& clusterState,
        const Bucket &bucket) const
{
    try {
        BucketOwnership pendingRes(
                _distributor.checkOwnershipInPendingState(bucket));
        if (!pendingRes.isOwned()) {
            return pendingRes;
        }
        uint16_t distributor = distribution.getIdealDistributorNode(
                clusterState, bucket.getBucketId());

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
IdealStateCalculator::check_ownership_in_pending_and_current_state_fallback(const Bucket &bucket) const
{
    auto &bucket_space(_bucket_space_repo.get(bucket.getBucketSpace()));
    return check_ownership_in_pending_and_given_state(bucket_space.getDistribution(), bucket_space.getClusterState(), bucket);
}

void
IdealStateCalculator::enumerate_available_nodes()
{
    const auto* up_states = _distributor.getStorageNodeUpStates();
    bool first = true;
    for (auto &bucket_space : _bucket_space_repo) {
        auto &s = bucket_space.second->getClusterState();
        if (first) {
            _distribution_bits = s.getDistributionBitCount();
        } else {
            // Distribution bits must be same for all bucket spaces
            assert(_distribution_bits == s.getDistributionBitCount());
        }
        first = false;
        auto *pending_s = _distributor.pendingClusterStateOrNull(bucket_space.first);
        auto node_count = s.getNodeCount(lib::NodeType::STORAGE);
        if (pending_s != nullptr) {
            node_count = std::min(node_count, pending_s->getNodeCount(lib::NodeType::STORAGE));
        }
        auto& nodes = _available_nodes[bucket_space.first];
        nodes.clear();
        nodes.resize(node_count);
        for (uint32_t i = 0; i < node_count; ++i) {
            lib::Node node_key(lib::NodeType::STORAGE, i);
            const lib::NodeState& ns(s.getNodeState(node_key));
            if (ns.getState().oneOf(up_states)) {
                if (pending_s == nullptr || pending_s->getNodeState(node_key).getState().oneOf(up_states)) {
                    nodes[i] = true;
                }
            }
        }
    }
}

const std::vector<bool>&
IdealStateCalculator::get_available_nodes(document::BucketSpace bucket_space) {
    auto itr = _available_nodes.find(bucket_space);
    if (itr == _available_nodes.end()) {
        return no_available_nodes;
    }
    return itr->second;
}

}
