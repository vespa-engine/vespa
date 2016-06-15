// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/clusterinformation.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage {
namespace distributor {

class SimpleClusterInformation : public ClusterInformation
{
public:
    SimpleClusterInformation(uint16_t myIndex,
                             const lib::Distribution& distribution,
                             const lib::ClusterState& clusterState,
                             const char* storageUpStates)
        : _myIndex(myIndex),
          _distribution(distribution.serialize()),
          _clusterState(clusterState),
          _storageUpStates(storageUpStates)
    {}

    uint16_t getDistributorIndex() const {
        return _myIndex;
    }

    const lib::Distribution& getDistribution() const {
        return _distribution;
    }

    const lib::ClusterState& getClusterState() const {
        return _clusterState;
    }

    const char* getStorageUpStates() const {
        return _storageUpStates;
    }

private:
    uint16_t _myIndex;
    lib::Distribution _distribution;
    lib::ClusterState _clusterState;
    const char* _storageUpStates;
};

}
}

