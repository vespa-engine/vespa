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
                             const lib::ClusterStateBundle& clusterStateBundle,
                             const char* storageUpStates)
        : _myIndex(myIndex),
          _clusterStateBundle(clusterStateBundle),
          _storageUpStates(storageUpStates)
    {}

    uint16_t getDistributorIndex() const override {
        return _myIndex;
    }

    const lib::ClusterStateBundle& getClusterStateBundle() const override {
        return _clusterStateBundle;
    }

    const char* getStorageUpStates() const override {
        return _storageUpStates;
    }

private:
    uint16_t _myIndex;
    lib::ClusterStateBundle _clusterStateBundle;
    const char* _storageUpStates;
};

}
