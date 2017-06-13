// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdint.h>
#include <vector>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/stllike/string.h>

namespace storage {

namespace lib {

class Distribution;
class ClusterState;

}

namespace distributor {

class ClusterInformation
{
public:
    typedef std::shared_ptr<const ClusterInformation> CSP;

    virtual ~ClusterInformation() {}

    virtual uint16_t getDistributorIndex() const = 0;

    virtual const lib::Distribution& getDistribution() const = 0;

    virtual const lib::ClusterState& getClusterState() const = 0;

    virtual const char* getStorageUpStates() const = 0;

    bool ownsBucket(const document::BucketId& bucketId) const;

    bool nodeInSameGroupAsSelf(uint16_t otherNode) const;

    vespalib::string getDistributionHash() const;

    std::vector<uint16_t> getIdealStorageNodesForState(
            const lib::ClusterState& clusterState,
            const document::BucketId& bucketId) const;

    uint16_t getStorageNodeCount() const;
};

}
}

