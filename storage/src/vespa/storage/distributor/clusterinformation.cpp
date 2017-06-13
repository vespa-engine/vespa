// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clusterinformation.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::distributor {

bool
ClusterInformation::ownsBucket(const document::BucketId& bucketId) const
{
    try {
        uint16_t distributor(getDistribution().getIdealDistributorNode(
                        getClusterState(), bucketId));

        return (getDistributorIndex() == distributor);
    } catch (lib::TooFewBucketBitsInUseException& e) {
        return false;
    } catch (lib::NoDistributorsAvailableException& e) {
        return false;
    }
}

bool
ClusterInformation::nodeInSameGroupAsSelf(uint16_t otherNode) const
{
    return (getDistribution().getNodeGraph().getGroupForNode(otherNode)
            == getDistribution().getNodeGraph().getGroupForNode(getDistributorIndex()));
}

vespalib::string
ClusterInformation::getDistributionHash() const
{
    return getDistribution().getNodeGraph().getDistributionConfigHash();
}

std::vector<uint16_t>
ClusterInformation::getIdealStorageNodesForState(
        const lib::ClusterState& clusterState,
        const document::BucketId& bucketId) const
{
    return getDistribution().getIdealStorageNodes(
            clusterState,
            bucketId,
            getStorageUpStates());
}

uint16_t
ClusterInformation::getStorageNodeCount() const
{
    return getClusterState().getNodeCount(lib::NodeType::STORAGE);
}

}
