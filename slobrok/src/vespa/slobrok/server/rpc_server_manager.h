// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ok_state.h"
#include "cmd.h"
#include "i_rpc_server_manager.h"
#include "named_service.h"
#include <vespa/fnet/task.h>
#include <vector>
#include <memory>

namespace slobrok {

class NamedService;
class ManagedRpcServer;
class RemoteSlobrok;
class ReservedName;
class RpcServerMap;
class ExchangeManager;
class SBEnv;

/**
 * @class RpcServerManager
 * @brief Main "business logic" for the service location broker.
 *
 * Used by all external and some internal operations.
 * This class actually implements operations,
 * checking for validity, manipulating internal datastructures,
 * and initiating synchronization operations to peer slobroks.
 **/
class RpcServerManager : public FNET_Task,
                         public IRpcServerManager
{
private:
    RpcServerMap &_rpcsrvmap;
    ExchangeManager &_exchanger;
    SBEnv &_env;

    struct MRSandRRSC {
        ManagedRpcServer *rpcsrv;
        ScriptCommand     handler;
        MRSandRRSC(ManagedRpcServer *d, ScriptCommand h)
            : rpcsrv(d), handler(std::move(h)) {}
    };
    std::vector<MRSandRRSC>         _addManageds;
    std::vector<std::unique_ptr<NamedService>> _deleteList;
public:
    OkState checkPartner(const std::string & remslobrok);

    OkState addPeer(const std::string & remsbname, const std::string & remsbspec);
    OkState removePeer(const std::string & remsbname, const std::string &  remsbspec);
    OkState addRemote(const std::string & name, const std::string & spec);

    OkState addRemReservation(const std::string & remslobrok, const std::string & name, const std::string & spec);
    OkState addMyReservation(const std::string & name, const std::string & spec);

    bool alreadyManaged(const std::string & name, const std::string & spec);
    void addManaged(ScriptCommand rdc);

    OkState remove(ManagedRpcServer *rpcsrv);

    OkState removeLocal(const std::string & name, const std::string & spec);
    OkState removeRemote(const std::string & name, const std::string & spec);

    RpcServerManager(const RpcServerManager &) = delete;
    RpcServerManager &operator=(const RpcServerManager &) = delete;
    RpcServerManager(SBEnv &sbenv);
    ~RpcServerManager();

    void PerformTask() override;
    void notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) override;
    void notifyOkRpcSrv(ManagedRpcServer *rpcsrv) override;
    FRT_Supervisor *getSupervisor() override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok

