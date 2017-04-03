// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <deque>
#include <string>

#include <vespa/fnet/frt/frt.h>

#include <vespa/vespalib/util/hashmap.h>
#include "ok_state.h"
#include "cmd.h"
#include "i_rpc_server_manager.h"
#include "rpc_server_manager.h"
#include "managed_rpc_server.h"

namespace slobrok {

//-----------------------------------------------------------------------------

class SBEnv;
class RpcServerMap;
class RpcServerManager;
class ExchangeManager;

using vespalib::HashMap;

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
    RemoteSlobrok(const RemoteSlobrok&); // not used
    RemoteSlobrok& operator= (const RemoteSlobrok&); // not used

    class Reconnecter : public FNET_Task
    {
    private:
        int            _waittime;
        RemoteSlobrok &_owner;
        Reconnecter(const Reconnecter &); // not used
        Reconnecter &operator=(const Reconnecter &); // not used
    public:
        explicit Reconnecter(FNET_Scheduler *sched, RemoteSlobrok &owner);
        ~Reconnecter();
        void scheduleTryConnect();
        void disable();
        void PerformTask() override;
    };

private:
    ExchangeManager     &_exchanger;
    RpcServerManager    &_rpcsrvmanager;
    FRT_Target          *_remote;
    ManagedRpcServer     _rpcserver;
    Reconnecter          _reconnecter;
    int                  _failCnt;

    FRT_RPCRequest      *_remAddPeerReq;
    FRT_RPCRequest      *_remListReq;
    FRT_RPCRequest      *_remAddReq;
    FRT_RPCRequest      *_remRemReq;

    std::deque<NamedService *> _pending;
    void pushMine();
    void doPending();

public:
    RemoteSlobrok(const char *name, const char *spec,
                  ExchangeManager &manager);
    ~RemoteSlobrok();

    void fail();
    bool isConnected() const { return (_remote != NULL); }
    void tryConnect();
    void healthCheck();
    void invokeAsync(FRT_RPCRequest *req,
                     double timeout,
                     FRT_IRequestWait *rwaiter);
    const char *getName() const { return _rpcserver.getName(); }
    const char *getSpec() const { return _rpcserver.getSpec(); }

    // interfaces implemented:
    void notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) override;
    void notifyOkRpcSrv(ManagedRpcServer *rpcsrv) override;
    void RequestDone(FRT_RPCRequest *req) override;
    FRT_Supervisor *getSupervisor() override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok
