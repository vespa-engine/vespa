// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>

#include <vespa/vespalib/util/hashmap.h>

#include <vespa/fnet/frt/frt.h>

#include "ok_state.h"
#include "cmd.h"
#include "i_rpc_server_manager.h"
#include "named_service.h"

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
        RegRpcSrvCommand      handler;
        MRSandRRSC(ManagedRpcServer *d, RegRpcSrvCommand h)
            : rpcsrv(d), handler(h) {}

        MRSandRRSC(const MRSandRRSC &rhs)
            : rpcsrv(rhs.rpcsrv),
              handler(rhs.handler)
        {
        }

        MRSandRRSC& operator=(const MRSandRRSC &rhs)
        {
            rpcsrv = rhs.rpcsrv;
            handler = rhs.handler;
            return *this;
        }
    };
    std::vector<MRSandRRSC>         _addManageds;
    std::vector<ManagedRpcServer *> _deleteList;

    RpcServerManager(const RpcServerManager &);            // Not used
    RpcServerManager &operator=(const RpcServerManager &); // Not used

public:
    OkState checkPartner(const char *remslobrok);

    OkState addPeer(const char *remsbname,
                    const char *remsbspec);
    OkState removePeer(const char *remsbname,
                       const char *remsbspec);

    OkState addLocal(const char *name,
                     const char *spec,
                     FRT_RPCRequest *req);
    OkState addRemote(const char *name,
                      const char *spec);

    OkState addRemReservation(const char *remslobrok,
                              const char *name,
                              const char *spec);
    OkState addMyReservation(const char *name,
                           const char *spec);

    bool alreadyManaged(const char *name,
                        const char *spec);
    void addManaged(const char *name,
                    const char *spec,
                    RegRpcSrvCommand rdc);

    OkState remove(ManagedRpcServer *rpcsrv);

    OkState removeLocal(const char *name, const char *spec);

    OkState removeRemote(const char *name,
                         const char *spec);

    RpcServerManager(SBEnv &sbenv);
    ~RpcServerManager();

    void PerformTask() override;
    void notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg) override;
    void notifyOkRpcSrv(ManagedRpcServer *rpcsrv) override;
    FRT_Supervisor *getSupervisor() override;
};

//-----------------------------------------------------------------------------

} // namespace slobrok

