// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "clusterinformation.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::distributor {

class SimpleClusterInformation : public ClusterInformation
{
public:
    SimpleClusterInformation(uint16_t myIndex,
                             const lib::ClusterState& clusterState,
                             const char* storageUpStates)
        : _myIndex(myIndex),
          _clusterState(clusterState),
          _storageUpStates(storageUpStates)
    {}

    uint16_t getDistributorIndex() const override {
        return _myIndex;
    }

    const lib::ClusterState& getClusterState() const override {
        return _clusterState;
    }

    const char* getStorageUpStates() const override {
        return _storageUpStates;
    }

private:
    uint16_t _myIndex;
    lib::ClusterState _clusterState;
    const char* _storageUpStates;
};

}
