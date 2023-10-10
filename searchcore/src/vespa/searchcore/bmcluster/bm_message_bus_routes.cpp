// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_message_bus_routes.h"
#include <vespa/storageapi/messageapi/storagemessage.h>

namespace search::bmcluster {

BmMessageBusRoutes::BmMessageBusRoutes(uint32_t num_nodes, bool distributor)
    : BmStorageMessageAddresses(num_nodes, distributor),
      _routes(num_nodes)
{
    for (uint32_t node_idx = 0; node_idx < num_nodes; ++node_idx) {
        _routes[node_idx] = get_address(node_idx).to_mbus_route();
    }
}

BmMessageBusRoutes::~BmMessageBusRoutes() = default;

}
