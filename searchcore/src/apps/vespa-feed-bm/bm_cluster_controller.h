// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace storage::api { class StorageMessageAddress; }
namespace storage::rpc { class SharedRpcResources; }

namespace feedbm {

/*
 * Fake cluster controller that sets cluster state to be up.
 */
class BmClusterController
{
    storage::rpc::SharedRpcResources& _shared_rpc_resources;
public:
    BmClusterController(storage::rpc::SharedRpcResources& shared_rpc_resources_in);
    void set_cluster_up(bool distributor);
};

}
