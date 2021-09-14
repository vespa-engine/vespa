// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace storage::api { class StorageMessageAddress; }
namespace storage::rpc { class SharedRpcResources; }

namespace search::bmcluster {

/*
 * Fake cluster controller that sets cluster state to be up.
 */
class BmClusterController
{
    storage::rpc::SharedRpcResources& _shared_rpc_resources;
    uint32_t                          _num_nodes;
public:
    BmClusterController(storage::rpc::SharedRpcResources& shared_rpc_resources_in, uint32_t num_nodes);
    void set_cluster_up(uint32_t node_idx, bool distributor);
};

}
