// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/frt/invokable.h>
#include <atomic>
#include <memory>

class FRT_RPCRequest;

namespace storage {

class MessageDispatcher;

namespace api {
class StorageCommand;
class StorageMessage;
class StorageMessageAddress;
class StorageReply;
}

namespace rpc {

class SharedRpcResources;

class ClusterControllerApiRpcService : public FRT_Invokable {
    MessageDispatcher&  _message_dispatcher;
    std::atomic<bool>   _closed;
public:
    static constexpr uint32_t StateBundleMaxUncompressedSize = 1024 * 1024 * 16;

    ClusterControllerApiRpcService(MessageDispatcher& message_dispatcher,
                                   SharedRpcResources& rpc_resources);
    ~ClusterControllerApiRpcService() override;

    void close();

    void RPC_getNodeState2(FRT_RPCRequest* req);
    void RPC_setSystemState2(FRT_RPCRequest* req);
    void RPC_setDistributionStates(FRT_RPCRequest* req);
    void RPC_activateClusterStateVersion(FRT_RPCRequest* req);
private:
    void register_server_methods(SharedRpcResources&);
    // TODO factor out as shared functionality
    void detach_and_forward_to_enqueuer(std::shared_ptr<api::StorageMessage> cmd, FRT_RPCRequest* req);
};

} // rpc
} // storage
