// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bm_storage_message_addresses.h"
#include <vespa/messagebus/routing/route.h>

namespace search::bmcluster {

/*
 * Class containing the message bus routes for a set of nodes at
 * the given layer (service layer or distributor).
 */
class BmMessageBusRoutes : public BmStorageMessageAddresses
{
    std::vector<mbus::Route> _routes;
public:
    BmMessageBusRoutes(uint32_t num_nodes, bool distributor);
    ~BmMessageBusRoutes();
    const mbus::Route& get_route(uint32_t node_idx) const { return _routes[node_idx]; }
    bool has_route(uint32_t node_idx) const { return node_idx < _routes.size(); }
};

}
