// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include "cluster_state_bundle.h"
#include "clusterstate.h"
#include <iostream>
#include <sstream>

namespace storage::lib {

ClusterStateBundle::FeedBlock::FeedBlock(bool block_feed_in_cluster_in,
                                         const vespalib::string& description_in)
    : _block_feed_in_cluster(block_feed_in_cluster_in),
      _description(description_in)
{
}

bool
ClusterStateBundle::FeedBlock::operator==(const FeedBlock& rhs) const noexcept
{
    return (_block_feed_in_cluster == rhs._block_feed_in_cluster) &&
            (_description == rhs._description);
}

ClusterStateBundle::ClusterStateBundle(std::shared_ptr<const ClusterState> baseline_cluster_state)
    : _baselineClusterState(std::move(baseline_cluster_state)),
      _derivedBucketSpaceStates(),
      _feed_block(),
      _distribution_bundle(),
      _deferredActivation(false)
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterState& baselineClusterState)
    : ClusterStateBundle(std::make_shared<const ClusterState>(baselineClusterState))
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterState& baselineClusterState,
                                       BucketSpaceStateMapping derivedBucketSpaceStates)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState)),
      _derivedBucketSpaceStates(std::move(derivedBucketSpaceStates)),
      _feed_block(),
      _distribution_bundle(),
      _deferredActivation(false)
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterState& baselineClusterState,
                                       BucketSpaceStateMapping derivedBucketSpaceStates,
                                       bool deferredActivation)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState)),
      _derivedBucketSpaceStates(std::move(derivedBucketSpaceStates)),
      _feed_block(),
      _distribution_bundle(),
      _deferredActivation(deferredActivation)
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterState& baselineClusterState,
                                       BucketSpaceStateMapping derivedBucketSpaceStates,
                                       const FeedBlock& feed_block_in,
                                       bool deferredActivation)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState)),
      _derivedBucketSpaceStates(std::move(derivedBucketSpaceStates)),
      _feed_block(feed_block_in),
      _distribution_bundle(),
      _deferredActivation(deferredActivation)
{
}

ClusterStateBundle::ClusterStateBundle(std::shared_ptr<const ClusterState> baseline_cluster_state,
                                       BucketSpaceStateMapping derived_bucket_space_states,
                                       std::optional<FeedBlock> feed_block_in,
                                       std::shared_ptr<const DistributionConfigBundle> distribution_bundle,
                                       bool deferred_activation)
    : _baselineClusterState(std::move(baseline_cluster_state)),
      _derivedBucketSpaceStates(std::move(derived_bucket_space_states)),
      _feed_block(std::move(feed_block_in)),
      _distribution_bundle(std::move(distribution_bundle)),
      _deferredActivation(deferred_activation)
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterStateBundle&) = default;
ClusterStateBundle& ClusterStateBundle::operator=(const ClusterStateBundle&) = default;
ClusterStateBundle::ClusterStateBundle(ClusterStateBundle&&) noexcept = default;
ClusterStateBundle& ClusterStateBundle::operator=(ClusterStateBundle&&) noexcept = default;

ClusterStateBundle::~ClusterStateBundle() = default;

std::shared_ptr<const ClusterStateBundle>
ClusterStateBundle::clone_with_new_distribution(std::shared_ptr<const DistributionConfigBundle> distribution) const
{
    return std::make_shared<const ClusterStateBundle>(_baselineClusterState, _derivedBucketSpaceStates, _feed_block,
                                                      std::move(distribution), _deferredActivation);
}

const std::shared_ptr<const lib::ClusterState>&
ClusterStateBundle::getBaselineClusterState() const
{
    return _baselineClusterState;
}

const std::shared_ptr<const lib::ClusterState>&
ClusterStateBundle::getDerivedClusterState(document::BucketSpace bucketSpace) const
{
    auto itr = _derivedBucketSpaceStates.find(bucketSpace);
    if (itr != _derivedBucketSpaceStates.end()) {
        return itr->second;
    }
    return _baselineClusterState;
}

std::shared_ptr<const Distribution>
ClusterStateBundle::bucket_space_distribution_or_nullptr(document::BucketSpace space) const noexcept {
    if (!_distribution_bundle) {
        return {};
    }
    return _distribution_bundle->bucket_space_distribution_or_nullptr(space);
}

uint32_t
ClusterStateBundle::getVersion() const
{
    return _baselineClusterState->getVersion();
}

bool
ClusterStateBundle::operator==(const ClusterStateBundle& rhs) const noexcept
{
    if (!(*_baselineClusterState == *rhs._baselineClusterState)) {
        return false;
    }
    if (_derivedBucketSpaceStates.size() != rhs._derivedBucketSpaceStates.size()) {
        return false;
    }
    if (_distribution_bundle && rhs._distribution_bundle) {
        if (*_distribution_bundle != *rhs._distribution_bundle) {
            return false;
        }
    } else if (_distribution_bundle || rhs._distribution_bundle) {
        return false; // either side, but not both, had distribution config set
    }
    if (_feed_block != rhs._feed_block) {
        return false;
    }
    if (_deferredActivation != rhs._deferredActivation) {
        return false;
    }
    // Can't do a regular operator== comparison since we must check equality
    // of cluster state _values_, not their _pointers_.
    for (auto& lhs_ds : _derivedBucketSpaceStates) {
        auto rhs_iter = rhs._derivedBucketSpaceStates.find(lhs_ds.first);
        if ((rhs_iter == rhs._derivedBucketSpaceStates.end())
            || !(*lhs_ds.second == *rhs_iter->second)) {
            return false;
        }
    }
    return true;
}

std::string
ClusterStateBundle::toString() const
{
    std::ostringstream os;
    os << *this;
    return os.str();
}

std::ostream& operator<<(std::ostream& os, const ClusterStateBundle& bundle) {
    os << "ClusterStateBundle('" << *bundle.getBaselineClusterState();
    if (!bundle.getDerivedClusterStates().empty()) {
        // Output ordering is undefined for of per-space states.
        for (auto& ds : bundle.getDerivedClusterStates()) {
            os << "', ";
            os << document::FixedBucketSpaces::to_string(ds.first);
            os << " '" << *ds.second;
        }
    }
    os << '\'';
    if (bundle.block_feed_in_cluster()) {
        os << ", feed blocked: '" << bundle.feed_block()->description() << "'";
    }
    if (auto* distr = bundle.distribution_config_bundle_or_nullptr()) {
        os << ", distribution config: " << distr->total_leaf_group_count() << " group(s); "
           << distr->total_node_count() << " node(s); redundancy "
           << distr->redundancy() << "; searchable-copies " << distr->searchable_copies();
    }
    if (bundle.deferredActivation()) {
        os << " (deferred activation)";
    }
    os << ")";
    return os;
}

}
