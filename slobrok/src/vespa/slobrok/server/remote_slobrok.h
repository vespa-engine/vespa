// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"
#include "cmd.h"
#include "i_rpc_server_manager.h"
#include "managed_rpc_server.h"
#include "service_map_mirror.h"
#include <deque>

namespace slobrok {

//-----------------------------------------------------------------------------

class RpcServerManager;
class ExchangeManager;

//-----------------------------------------------------------------------------

/**
 * @class RemoteSlobrok
 * @brief Keeps track of and talks to a remote location broker
 *
 * Handles one single partner slobrok
 **/
class RemoteSlobrok: public FRT_IRequestWait
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

    vespalib::string     _name;
    vespalib::string     _spec;
    ExchangeManager     &_exchanger;
    RpcServerManager    &_rpcsrvmanager;
    FRT_Target          *_remote;
    ServiceMapMirror     _serviceMapMirror;
    Reconnecter          _reconnecter;
    int                  _failCnt;

    FRT_RPCRequest      *_remAddPeerReq;
    FRT_RPCRequest      *_remListReq;
    FRT_RPCRequest      *_remAddReq;
    FRT_RPCRequest      *_remRemReq;
    FRT_RPCRequest      *_remFetchReq;
    FRT_RPCRequest      *_checkServerReq;

    std::deque<std::unique_ptr<NamedService>> _pending;
    void pushMine();
    void doPending();
    void handleCheckServerResult();
    void handleFetchResult();

public:
    RemoteSlobrok(const RemoteSlobrok&) = delete;
    RemoteSlobrok& operator= (const RemoteSlobrok&) = delete;
    RemoteSlobrok(const vespalib::string &name, const vespalib::string &spec, ExchangeManager &manager);
    ~RemoteSlobrok() override;

    void fail();
    bool isConnected() const { return (_remote != nullptr); }
    void tryConnect();
    void maybePushMine();
    void maybeStartFetch();
    void invokeAsync(FRT_RPCRequest *req, double timeout, FRT_IRequestWait *rwaiter);
    const vespalib::string & getName() const { return _name; }
    const vespalib::string & getSpec() const { return _spec; }
    ServiceMapMirror &remoteMap() { return _serviceMapMirror; }
    void shutdown();
    FRT_Supervisor *getSupervisor();

    // interfaces implemented:
    void RequestDone(FRT_RPCRequest *req) override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok
