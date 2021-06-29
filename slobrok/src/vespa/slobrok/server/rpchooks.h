// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/frt/invokable.h>
#include <memory>

class FNET_Task;
class FRT_Supervisor;

namespace slobrok {

class SBEnv;
class RpcServerMap;
class RpcServerManager;

/**
 * @class RPCHooks
 * @brief The FNET-RPC interface to a location broker
 *
 * Contains methods for receiveing and unpacking requests,
 * invoking the right internal method, and (in most cases)
 * packaging and returning the result of the request.
 **/
class RPCHooks : public FRT_Invokable
{
public:

    struct Metrics {
        unsigned long heartBeatFails;
        unsigned long registerReqs;
        unsigned long mirrorReqs;
        unsigned long wantAddReqs;
        unsigned long doAddReqs;
        unsigned long doRemoveReqs;
        unsigned long adminReqs;
        unsigned long otherReqs;
        static Metrics zero() { return Metrics{0,0,0,0,0,0,0,0}; }
    };

private:
    SBEnv &_env;
    RpcServerMap &_rpcsrvmap;
    RpcServerManager &_rpcsrvmanager;

    Metrics _cnts;
    std::unique_ptr<FNET_Task> _m_reporter;

public:
    RPCHooks(SBEnv &env, RpcServerMap& rpcsrvmap, RpcServerManager& rpcsrvman);
    ~RPCHooks() override;

    void initRPC(FRT_Supervisor *supervisor);
    void reportMetrics();
    const Metrics& getMetrics() const { return _cnts; }
    void countFailedHeartbeat() { _cnts.heartBeatFails++; }

private:
    void rpc_lookupRpcServer(FRT_RPCRequest *req);

    void rpc_registerRpcServer(FRT_RPCRequest *req);
    void rpc_unregisterRpcServer(FRT_RPCRequest *req);

    void rpc_addPeer(FRT_RPCRequest *req);
    void rpc_removePeer(FRT_RPCRequest *req);
    void rpc_listManagedRpcServers(FRT_RPCRequest *req);
    void rpc_lookupManaged(FRT_RPCRequest *req);
    void rpc_listAllRpcServers(FRT_RPCRequest *req);
    void rpc_incrementalFetch(FRT_RPCRequest *req);
    void rpc_wantAdd(FRT_RPCRequest *req);
    void rpc_doAdd(FRT_RPCRequest *req);
    void rpc_doRemove(FRT_RPCRequest *req);
    void rpc_fetchLocalView(FRT_RPCRequest *req);

    void rpc_listNamesServed(FRT_RPCRequest *req);
    void rpc_getRpcServerHistory(FRT_RPCRequest *req);

    void rpc_stop(FRT_RPCRequest *req);
    void rpc_version(FRT_RPCRequest *req);
};

} // namespace slobrok

