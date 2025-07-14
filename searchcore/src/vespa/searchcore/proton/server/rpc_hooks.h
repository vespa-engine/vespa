// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/slobrok/sbregister.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/searchlib/engine/proto_rpc_adapter.h>
#include <string>

class FNET_Transport;

namespace proton {

class DetachedRpcRequestsOwner;
class Proton;

class RPCHooksBase : public FRT_Invokable
{
private:
    using ProtoRpcAdapter = search::engine::ProtoRpcAdapter;

    Proton                         & _proton;
    std::unique_ptr<FNET_Transport>  _transport;
    std::shared_ptr<DetachedRpcRequestsOwner> _detached_requests_owner;
    std::unique_ptr<FRT_Supervisor>  _orb;
    std::unique_ptr<ProtoRpcAdapter> _proto_rpc_adapter;
    slobrok::api::RegisterAPI        _regAPI;

    void initRPC();
    void letProtonDo(vespalib::Executor::Task::UP task);

    void triggerFlush(FRT_RPCRequest *req);
    void prepareRestart(FRT_RPCRequest *req);
    void prepareRestart2(FRT_RPCRequest *req);
    void reportState(FRT_RPCRequest * req) __attribute__((noinline));
    void getProtonStatus(FRT_RPCRequest * req);

public:
    using UP = std::unique_ptr<RPCHooksBase>;
    struct Params {
        Proton           &proton;
        config::ConfigUri slobrok_config;
        std::string  identity;
        uint32_t          rtcPort;
        uint32_t          numTranportThreads;

        Params(Proton &parent, uint32_t port, const config::ConfigUri & configUri,
               std::string_view slobrokId, uint32_t numTransportThreads);
        ~Params();
    };
    RPCHooksBase(const RPCHooksBase &) = delete;
    RPCHooksBase & operator = (const RPCHooksBase &) = delete;
    RPCHooksBase(Params &params);
    auto &proto_rpc_adapter_metrics() { return _proto_rpc_adapter->metrics(); }
    void set_online();
    ~RPCHooksBase() override;
    void close();

    void rpc_GetState(FRT_RPCRequest *req);
    void rpc_GetProtonStatus(FRT_RPCRequest *req);
    void rpc_triggerFlush(FRT_RPCRequest *req);
    void rpc_prepareRestart(FRT_RPCRequest *req);
    void rpc_prepareRestart2(FRT_RPCRequest *req);
protected:
    void open(Params & params);
};

class RPCHooks : public RPCHooksBase
{
public:
    RPCHooks(Params &params);
};

}
