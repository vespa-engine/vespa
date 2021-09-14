// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace storage::api { class StorageMessageAddress; }
namespace storage::rpc { class SharedRpcResources; }

namespace search::bmcluster {

class IBmDistribution;

/*
 * Fake cluster controller that sets cluster state to be up.
 */
class BmClusterController
{
    storage::rpc::SharedRpcResources& _shared_rpc_resources;
    const IBmDistribution&            _distribution;
public:
    BmClusterController(storage::rpc::SharedRpcResources& shared_rpc_resources_in, const IBmDistribution& distribution);
    void set_cluster_up(uint32_t node_idx, bool distributor);
};

}
