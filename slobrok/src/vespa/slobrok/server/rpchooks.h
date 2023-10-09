// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/frt/invokable.h>
#include <memory>

class FNET_Task;
class FRT_Supervisor;

namespace slobrok {

class SBEnv;
class RpcServerMap;
class RpcServerManager;
class ServiceMapHistory;

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
        unsigned long missingConsensusTime;
        static Metrics zero() { return Metrics{0,0,0,0,0,0,0,0,0}; }
    };

private:
    SBEnv &_env;
    ServiceMapHistory &_globalHistory;
    ServiceMapHistory &_localHistory;

    Metrics _cnts;
    std::unique_ptr<FNET_Task> _m_reporter;

public:
    RPCHooks(SBEnv &env);
    ~RPCHooks() override;

    void initRPC(FRT_Supervisor *supervisor);
    void reportMetrics();
    const Metrics& getMetrics() const { return _cnts; }
    void countFailedHeartbeat() { _cnts.heartBeatFails++; }
    void setConsensusTime(unsigned long value) {
        _cnts.missingConsensusTime = value;
    }
private:
    void rpc_registerRpcServer(FRT_RPCRequest *req);
    void rpc_unregisterRpcServer(FRT_RPCRequest *req);
    void rpc_addPeer(FRT_RPCRequest *req);
    void rpc_removePeer(FRT_RPCRequest *req);
    void rpc_incrementalFetch(FRT_RPCRequest *req);
    void rpc_doRemove(FRT_RPCRequest *req);
    void rpc_fetchLocalView(FRT_RPCRequest *req);
    void rpc_listNamesServed(FRT_RPCRequest *req);

    /** for unit tests and debugging, consider removing some of these: */
    void rpc_lookupRpcServer(FRT_RPCRequest *req);
    void rpc_listManagedRpcServers(FRT_RPCRequest *req);
    void rpc_lookupManaged(FRT_RPCRequest *req);
    void rpc_listAllRpcServers(FRT_RPCRequest *req);
    void rpc_wantAdd(FRT_RPCRequest *req);
    void rpc_doAdd(FRT_RPCRequest *req);

    /** consider removing: */
    void rpc_stop(FRT_RPCRequest *req);
    void rpc_version(FRT_RPCRequest *req);
};

} // namespace slobrok

