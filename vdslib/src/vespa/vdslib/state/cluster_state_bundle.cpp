// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include "cluster_state_bundle.h"
#include "clusterstate.h"
#include <iostream>

namespace storage::lib {

ClusterStateBundle::ClusterStateBundle(const ClusterState &baselineClusterState)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState))
{
}

ClusterStateBundle::ClusterStateBundle(const ClusterState& baselineClusterState,
                                       BucketSpaceStateMapping derivedBucketSpaceStates)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState)),
      _derivedBucketSpaceStates(std::move(derivedBucketSpaceStates))
{
}

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
ClusterStateBundle::operator==(const ClusterStateBundle &rhs) const
{
    if (!(*_baselineClusterState == *rhs._baselineClusterState)) {
        return false;
    }
    if (_derivedBucketSpaceStates.size() != rhs._derivedBucketSpaceStates.size()) {
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
    os << "')";
    return os;
}

}
