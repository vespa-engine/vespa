// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "clusterinformation.h"
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

namespace storage::distributor {

uint16_t
ClusterInformation::getStorageNodeCount() const
{
    return getClusterState().getNodeCount(lib::NodeType::STORAGE);
}

}
