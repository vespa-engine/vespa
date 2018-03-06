// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cluster_state_bundle.h"
#include "clusterstate.h"

namespace storage::lib {

ClusterStateBundle::ClusterStateBundle(const ClusterState &baselineClusterState)
    : _baselineClusterState(std::make_shared<const ClusterState>(baselineClusterState))
{
}

ClusterStateBundle::~ClusterStateBundle() = default;

const std::shared_ptr<const lib::ClusterState> &
ClusterStateBundle::getBaselineClusterState() const
{
    return _baselineClusterState;
}

const std::shared_ptr<const lib::ClusterState> &
ClusterStateBundle::getDerivedClusterState(document::BucketSpace) const
{
    // For now, just return the baseline cluster state.
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
    return *_baselineClusterState == *rhs._baselineClusterState;
}

}
