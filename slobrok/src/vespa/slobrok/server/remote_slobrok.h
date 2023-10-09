// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"
#include "i_rpc_server_manager.h"
#include "managed_rpc_server.h"
#include "service_map_mirror.h"
#include <deque>

namespace slobrok {

//-----------------------------------------------------------------------------

class ExchangeManager;

//-----------------------------------------------------------------------------

/**
 * @class RemoteSlobrok
 * @brief Keeps track of and talks to a remote location broker
 *
 * Handles one single partner slobrok
 **/
class RemoteSlobrok: public IRpcServerManager,
                     public FRT_IRequestWait
{
private:
    class Reconnecter : public FNET_Task
    {
    private:
        int            _waittime;
        RemoteSlobrok &_owner;
        Reconnecter(const Reconnecter &); // not used
        Reconnecter &operator=(const Reconnecter &); // not used
    public:
        explicit Reconnecter(FNET_Scheduler *sched, RemoteSlobrok &owner);
        ~Reconnecter() override;
        void scheduleTryConnect();
        void disable();
        void PerformTask() override;
    };

    ExchangeManager     &_exchanger;
    FRT_Target          *_remote;
    ServiceMapMirror     _serviceMapMirror;
    ManagedRpcServer     _rpcserver;
    Reconnecter          _reconnecter;
    int                  _failCnt;

    std::unique_ptr<MapSubscription> _consensusSubscription;

    FRT_RPCRequest      *_remAddPeerReq;
    FRT_RPCRequest      *_remFetchReq;

    void handleFetchResult();

public:
    RemoteSlobrok(const RemoteSlobrok&) = delete;
    RemoteSlobrok& operator= (const RemoteSlobrok&) = delete;
    RemoteSlobrok(const std::string &name, const std::string &spec, ExchangeManager &manager);
    ~RemoteSlobrok() override;

    void fail();
    bool isConnected() const { return (_remote != nullptr); }
    void tryConnect();
    void maybeStartFetch();
    void invokeAsync(FRT_RPCRequest *req, double timeout, FRT_IRequestWait *rwaiter);
    const std::string & getName() const { return _rpcserver.getName(); }
    const std::string & getSpec() const { return _rpcserver.getSpec(); }
    ServiceMapMirror &remoteMap() { return _serviceMapMirror; }
    void shutdown();

    // interfaces implemented:
    void notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) override;
    void notifyOkRpcSrv(ManagedRpcServer *rpcsrv) override;
    void RequestDone(FRT_RPCRequest *req) override;
    FRT_Supervisor *getSupervisor() override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok
