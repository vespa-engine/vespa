// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/slobrok/sbregister.h>
#include <atomic>

class FNET_Transport;
class FastOS_ThreadPool;

namespace storage {

namespace api { class StorageMessage; }

class MessageEnqueuer;

class FNetListener :  public FRT_Invokable
{
public:
    static constexpr uint32_t StateBundleMaxUncompressedSize = 1024 * 1024 * 16;

    FNetListener(MessageEnqueuer& messageEnqueuer,
                 const config::ConfigUri & configUri,
                 uint32_t port);
    ~FNetListener() override;

    void initRPC();
    void RPC_getNodeState2(FRT_RPCRequest *req);
    void RPC_setSystemState2(FRT_RPCRequest *req);
    void RPC_getCurrentTime(FRT_RPCRequest *req);
    void RPC_setDistributionStates(FRT_RPCRequest* req);
    void RPC_activateClusterStateVersion(FRT_RPCRequest* req);

    void registerHandle(vespalib::stringref handle);
    void close();
    int getListenPort() const;

private:
    MessageEnqueuer&                   _messageEnqueuer;
    std::unique_ptr<FastOS_ThreadPool> _threadPool;
    std::unique_ptr<FNET_Transport>    _transport;
    std::unique_ptr<FRT_Supervisor>    _orb;
    std::atomic<bool>                  _closed;
    slobrok::api::RegisterAPI          _slobrokRegister;
    vespalib::string                   _handle;

    void detach_and_forward_to_enqueuer(std::shared_ptr<api::StorageMessage> cmd, FRT_RPCRequest *req);
};

}
