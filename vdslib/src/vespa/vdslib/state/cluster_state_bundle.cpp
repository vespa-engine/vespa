// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
ClusterStateBundle::FeedBlock::operator==(const FeedBlock& rhs) const
{
    return (_block_feed_in_cluster == rhs._block_feed_in_cluster) &&
            (_description == rhs._description);
}

ClusterStateBundle::ClusterStateBundle(const ClusterState &baselineClusterState)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState)),
      _derivedBucketSpaceStates(),
      _feed_block(),
      _deferredActivation(false)
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterState& baselineClusterState,
                                       BucketSpaceStateMapping derivedBucketSpaceStates)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState)),
      _derivedBucketSpaceStates(std::move(derivedBucketSpaceStates)),
      _feed_block(),
      _deferredActivation(false)
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterState& baselineClusterState,
                                       BucketSpaceStateMapping derivedBucketSpaceStates,
                                       bool deferredActivation)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState)),
      _derivedBucketSpaceStates(std::move(derivedBucketSpaceStates)),
      _feed_block(),
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
      _deferredActivation(deferredActivation)
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterStateBundle&) = default;
ClusterStateBundle& ClusterStateBundle::operator=(const ClusterStateBundle&) = default;
ClusterStateBundle::ClusterStateBundle(ClusterStateBundle&&) noexcept = default;
ClusterStateBundle& ClusterStateBundle::operator=(ClusterStateBundle&&) noexcept = default;

ClusterStateBundle::~ClusterStateBundle() = default;

const std::shared_ptr<const lib::ClusterState> &
ClusterStateBundle::getBaselineClusterState() const
{
    return _baselineClusterState;
}

const std::shared_ptr<const lib::ClusterState> &
ClusterStateBundle::getDerivedClusterState(document::BucketSpace bucketSpace) const
{
    auto itr = _derivedBucketSpaceStates.find(bucketSpace);
    if (itr != _derivedBucketSpaceStates.end()) {
        return itr->second;
    }
    return _baselineClusterState;
}

uint32_t
ClusterStateBundle::getVersion() const
{
    return _baselineClusterState->getVersion();
}

bool
ClusterStateBundle::operator==(const ClusterStateBundle &rhs) const noexcept
{
    if (!(*_baselineClusterState == *rhs._baselineClusterState)) {
        return false;
    }
    if (_derivedBucketSpaceStates.size() != rhs._derivedBucketSpaceStates.size()) {
        return false;
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
    if (bundle.deferredActivation()) {
        os << " (deferred activation)";
    }
    os << ")";
    return os;
}

}
