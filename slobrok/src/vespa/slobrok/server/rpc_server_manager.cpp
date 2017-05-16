// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>

#include <vespa/log/log.h>
LOG_SETUP(".rpcserver");

#include <string>
#include <sstream>
#include <vespa/vespalib/util/stringfmt.h>

#include "rpc_server_manager.h"
#include "ok_state.h"
#include "named_service.h"
#include "reserved_name.h"
#include "rpc_server_map.h"
#include "remote_slobrok.h"
#include "sbenv.h"

namespace slobrok {

RpcServerManager::RpcServerManager(SBEnv &sbenv)
    : FNET_Task(sbenv.getScheduler()),
      _rpcsrvmap(sbenv._rpcsrvmap),
      _exchanger(sbenv._exchanger),
      _env(sbenv),
      _addManageds(),
      _deleteList()
{
}

static OkState
validateName(const char *rpcsrvname)
{
    const char *p = rpcsrvname;
    while (*p != '\0') {
        // important: disallow '*'
        if (strchr("+,-./:=@[]_{}~<>"
                   "0123456789"
                   "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                   "abcdefghijklmnopqrstuvwxyz", *p) == NULL)
        {
            std::ostringstream tmp;
            tmp << "Illegal character '" << *p << "' (";
            tmp << (int)(*p)<< ") in rpcserver name";
            return OkState(13, tmp.str().c_str());
        }
        ++p;
    }
    if (p == rpcsrvname) {
        return OkState(13, "empty rpcserver name");
    }
    return OkState();
}


OkState
RpcServerManager::checkPartner(const char *remslobrok)
{
    if (strcmp(remslobrok, _env.mySpec()) == 0) {
        return OkState(13, "remote slobrok using my rpcserver name");
    }
    RemoteSlobrok *partner = _exchanger.lookupPartner(remslobrok);
    if (partner == NULL) {
        return OkState(13, "remote slobrok not a partner");
    }
    return OkState();
}

OkState
RpcServerManager::addRemReservation(const char *remslobrok,
                                    const char *name,
                                    const char *spec)
{
    OkState state = checkPartner(remslobrok);
    if (state.failed()) return state;

    OkState valid = validateName(name);
    if (valid.failed()) return valid;

    NamedService *old = _rpcsrvmap.lookupManaged(name);
    if (old != NULL) {
        if (strcmp(old->getSpec(), spec) == 0) {
            // was alright already
            return OkState(0, "already registered");
        }
        LOG(warning, "remote %s tried to register [%s -> %s] but we already have [%s -> %s] registered!",
            remslobrok, name, spec, old->getName(), old->getSpec());
        return OkState(FRTE_RPC_METHOD_FAILED, "already managed by me");
    }
    if (_rpcsrvmap.conflictingReservation(name, spec)) {
        return OkState(FRTE_RPC_METHOD_FAILED,
                       "registration for name already in progress");
    }
    ReservedName *rpcsrv = new ReservedName(name, spec, false);
    _rpcsrvmap.addReservation(rpcsrv);
    return OkState(0, "done");
}


OkState
RpcServerManager::addPeer(const char *remsbname,
                          const char *remsbspec)
{
    if (strcmp(remsbname, _env.mySpec()) == 0) {
        return OkState(13, "cannot add remote slobrok with my rpcserver name");
    }
    return _exchanger.addPartner(remsbname, remsbspec);
}


OkState
RpcServerManager::removePeer(const char *remsbname,
                             const char *remsbspec)
{
    if (strcmp(remsbname, _env.mySpec()) == 0) {
        return OkState(13, "cannot remove my own rpcserver name");
    }
    RemoteSlobrok *partner = _exchanger.lookupPartner(remsbname);
    if (partner == NULL) {
        return OkState(0, "remote slobrok not a partner");
    }
    if (strcmp(partner->getSpec(), remsbspec) != 0) {
        return OkState(13, "peer registered with different spec");
    }
    _exchanger.removePartner(remsbname);
    return OkState(0, "done");
}


OkState
RpcServerManager::addMyReservation(const char *name, const char *spec)
{
    OkState valid = validateName(name);
    if (valid.failed()) return valid;

    NamedService *old = _rpcsrvmap.lookupManaged(name);
    if (old != NULL) {
        if (strcmp(old->getSpec(), spec) == 0) {
            // was alright already
            return OkState(0, "already registered");
        } else {
            return OkState(FRTE_RPC_METHOD_FAILED, vespalib::make_string(
                          "name %s registered (to %s), cannot register %s",
                          name, old->getSpec(), spec));
        }
    }

    // check if we already are in the progress of adding this
    if (_rpcsrvmap.conflictingReservation(name, spec)) {
        ReservedName * rsv = _rpcsrvmap.getReservation(name);
        LOG(warning, "conflicting registrations: wanted [%s -> %s] but [%s -> %s] already reserved",
            name, spec, rsv->getName(), rsv->getSpec());
        return OkState(FRTE_RPC_METHOD_FAILED,
                       "registration for name already in progress with a different spec");
    }
    _rpcsrvmap.removeReservation(name);
    ReservedName *rpcsrv = new ReservedName(name, spec, true);
    _rpcsrvmap.addReservation(rpcsrv);
    return OkState(0, "done");
}


OkState
RpcServerManager::addRemote(const char *name,
                            const char *spec)
{
    OkState valid = validateName(name);
    if (valid.failed()) return valid;

    if (alreadyManaged(name, spec)) {
        return OkState(0, "already correct");
    }
    NamedService *old = _rpcsrvmap.lookup(name);
    if (old != NULL) {
        if (strcmp(old->getSpec(), spec) != 0) {
            LOG(warning, "collision on remote add: "
                "name %s registered to %s locally, "
                "but another location broker wants it registered to %s",
                name, old->getSpec(), spec);
            removeRemote(name, old->getSpec());
            return OkState(13, "registered, with different spec");
        }
        // was alright already, remove reservation
        _rpcsrvmap.removeReservation(name);
        return OkState(0, "already correct");
    }
    _rpcsrvmap.removeReservation(name);
    ManagedRpcServer *rpcsrv = new ManagedRpcServer(name, spec, *this);
    _rpcsrvmap.addNew(rpcsrv);
    rpcsrv->healthCheck();
    return OkState(0, "done");
}

OkState
RpcServerManager::remove(ManagedRpcServer *rpcsrv)
{
    NamedService *td = _rpcsrvmap.lookup(rpcsrv->getName());
    if (td == rpcsrv) {
        return removeLocal(rpcsrv->getName(), rpcsrv->getSpec());
    } else {
        return OkState(1, "not currently registered");
    }
}


OkState
RpcServerManager::removeRemote(const char *name,
                               const char *spec)
{
    NamedService *old = _rpcsrvmap.lookup(name);
    if (old == NULL) {
        // was alright already, remove any reservation too
        _rpcsrvmap.removeReservation(name);
        return OkState(0, "already done");
    }
    if (strcmp(old->getSpec(), spec) != 0) {
        return OkState(1, "name registered, but with different spec");
    }
    NamedService *td = _rpcsrvmap.remove(name);
    LOG_ASSERT(td == old);
    delete td;
    return OkState(0, "done");
}

OkState
RpcServerManager::removeLocal(const char *name, const char *spec)
{
    NamedService *td = _rpcsrvmap.lookup(name);
    if (td == NULL) {
        // already removed, nop
        return OkState();
    }

    RemoteSlobrok *partner = _exchanger.lookupPartner(name);
    if (partner != NULL) {
        return OkState(13, "cannot unregister partner slobrok");
    }

    ManagedRpcServer *rpcsrv = _rpcsrvmap.lookupManaged(name);
    if (rpcsrv == NULL) {
        return OkState(13, "not a local rpcserver");
    }

    if (strcmp(rpcsrv->getSpec(), spec) != 0) {
        // the client can probably ignore this "error"
        // or log it on level INFO?
        return OkState(1, "name registered, but with different spec");
    }
    td = _rpcsrvmap.remove(name);
    LOG_ASSERT(td == rpcsrv);
    delete rpcsrv;
    _exchanger.forwardRemove(name, spec);
    return OkState();
}


void
RpcServerManager::addManaged(const char *name, const char *spec,
                             RegRpcSrvCommand rdc)
{
    ManagedRpcServer *rpcsrv = new ManagedRpcServer(name, spec, *this);
    _rpcsrvmap.addNew(rpcsrv);
    for (size_t i = 0; i < _addManageds.size(); i++) {
        if (_addManageds[i].rpcsrv == NULL) {
            _addManageds[i].rpcsrv = rpcsrv;
            _addManageds[i].handler = rdc;
            rpcsrv->healthCheck();
            return;
        }
    }
    MRSandRRSC pair(rpcsrv, rdc);
    _addManageds.push_back(pair);
    rpcsrv->healthCheck();
    return;
}



bool
RpcServerManager::alreadyManaged(const char *name, const char *spec)
{
    ManagedRpcServer *rpcsrv = _rpcsrvmap.lookupManaged(name);
    if (rpcsrv != NULL) {
        if (strcmp(rpcsrv->getSpec(), spec) == 0) {
            return true;
        }
    }
    return false;
}


RpcServerManager::~RpcServerManager()
{
    Kill();
    PerformTask();
}


void
RpcServerManager::PerformTask()
{
    std::vector<ManagedRpcServer *> dl;
    std::swap(dl, _deleteList);
    for (uint32_t i = 0; i < dl.size(); ++i) {
        delete dl[i];
    }
}


void
RpcServerManager::notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg)
{
    _env.countFailedHeartbeat();
    bool logged = false;
    NamedService *old = _rpcsrvmap.lookup(rpcsrv->getName());
    if (old == rpcsrv) {
        old = _rpcsrvmap.remove(rpcsrv->getName());
        LOG_ASSERT(old == rpcsrv);
        LOG(info, "managed server %s at %s failed: %s",
            rpcsrv->getName(), rpcsrv->getSpec(), errmsg.c_str());
        logged = true;
    }
    _exchanger.forwardRemove(rpcsrv->getName(), rpcsrv->getSpec());
    for (size_t i = 0; i < _addManageds.size(); ++i) {
        if (_addManageds[i].rpcsrv == rpcsrv) {
            _addManageds[i].handler
                .doneHandler(OkState(13, "failed check using listNames callback"));
            _addManageds[i].rpcsrv = 0;
            LOG(warning, "rpcserver %s at %s failed while trying to register",
                rpcsrv->getName(), rpcsrv->getSpec());
            logged = true;
        }
    }
    if (!logged) {
        LOG(warning, "unmanaged server %s at %s failed: %s",
            rpcsrv->getName(), rpcsrv->getSpec(), errmsg.c_str());
    }
    _deleteList.push_back(rpcsrv);
    ScheduleNow();
}

void
RpcServerManager::notifyOkRpcSrv(ManagedRpcServer *rpcsrv)
{
    for (size_t i = 0; i < _addManageds.size(); ++i) {
        if (_addManageds[i].rpcsrv == rpcsrv) {
            _addManageds[i].handler.doneHandler(OkState());
            _addManageds[i].rpcsrv = 0;
        }
    }
    // XXX check if pending wantAdd / doAdd / registerRpcServer
}

FRT_Supervisor *
RpcServerManager::getSupervisor()
{
    return _env.getSupervisor();
}

//-----------------------------------------------------------------------------

} // namespace slobrok
