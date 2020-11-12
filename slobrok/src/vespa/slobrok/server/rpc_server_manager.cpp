// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_server_manager.h"
#include "reserved_name.h"
#include "rpc_server_map.h"
#include "remote_slobrok.h"
#include "sbenv.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".rpcserver");

using vespalib::make_string_short::fmt;

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
validateName(const std::string & rpcsrvname)
{
    const char *p = rpcsrvname.c_str();
    while (*p != '\0') {
        // important: disallow '*'
        if (strchr("+,-./:=@[]_{}~<>"
                   "0123456789"
                   "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                   "abcdefghijklmnopqrstuvwxyz", *p) == nullptr)
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
RpcServerManager::checkPartner(const std::string & remslobrok)
{
    if (remslobrok == _env.mySpec()) {
        return OkState(13, "remote slobrok using my rpcserver name");
    }
    const RemoteSlobrok *partner = _exchanger.lookupPartner(remslobrok);
    if (partner == nullptr) {
        return OkState(13, "remote slobrok not a partner");
    }
    return OkState();
}

OkState
RpcServerManager::addRemReservation(const std::string & remslobrok, const std::string & name, const std::string &spec)
{
    OkState state = checkPartner(remslobrok);
    if (state.failed()) return state;

    OkState valid = validateName(name);
    if (valid.failed()) return valid;

    const NamedService *old = _rpcsrvmap.lookupManaged(name);
    if (old != nullptr) {
        if (old->getSpec() == spec) {
            // was alright already
            return OkState(0, "already registered");
        }
        LOG(warning, "remote %s tried to register [%s -> %s] but we already have [%s -> %s] registered!",
            remslobrok.c_str(), name.c_str(), spec.c_str(), old->getName().c_str(), old->getSpec().c_str());
        return OkState(FRTE_RPC_METHOD_FAILED, "already managed by me");
    }
    if (_rpcsrvmap.conflictingReservation(name, spec)) {
        return OkState(FRTE_RPC_METHOD_FAILED, "registration for name already in progress");
    }
    _rpcsrvmap.addReservation(std::make_unique<ReservedName>(name, spec, false));
    return OkState(0, "done");
}


OkState
RpcServerManager::addPeer(const std::string & remsbname, const std::string &remsbspec)
{
    if (remsbname == _env.mySpec()) {
        return OkState(13, "cannot add remote slobrok with my rpcserver name");
    }
    return _exchanger.addPartner(remsbname, remsbspec);
}


OkState
RpcServerManager::removePeer(const std::string & remsbname, const std::string & remsbspec)
{
    if (remsbname == _env.mySpec()) {
        return OkState(13, "cannot remove my own rpcserver name");
    }
    const RemoteSlobrok *partner = _exchanger.lookupPartner(remsbname);
    if (partner == nullptr) {
        return OkState(0, "remote slobrok not a partner");
    }
    if (partner->getSpec() != remsbspec) {
        return OkState(13, "peer registered with different spec");
    }
    _exchanger.removePartner(remsbname);
    return OkState(0, "done");
}


OkState
RpcServerManager::addMyReservation(const std::string & name, const std::string & spec)
{
    OkState valid = validateName(name);
    if (valid.failed()) return valid;

    const NamedService *old = _rpcsrvmap.lookupManaged(name);
    if (old != nullptr) {
        if (old->getSpec() == spec) {
            // was alright already
            return OkState(0, "already registered");
        } else {
            return OkState(FRTE_RPC_METHOD_FAILED, fmt("name %s registered (to %s), cannot register %s",
                                                       name.c_str(), old->getSpec().c_str(), spec.c_str()));
        }
    }

    // check if we already are in the progress of adding this
    if (_rpcsrvmap.conflictingReservation(name, spec)) {
        const ReservedName * rsv = _rpcsrvmap.getReservation(name);
        LOG(warning, "conflicting registrations: wanted [%s -> %s] but [%s -> %s] already reserved",
            name.c_str(), spec.c_str(), rsv->getName().c_str(), rsv->getSpec().c_str());
        return OkState(FRTE_RPC_METHOD_FAILED,
                       "registration for name already in progress with a different spec");
    }
    _rpcsrvmap.removeReservation(name);
    _rpcsrvmap.addReservation(std::make_unique<ReservedName>(name, spec, true));
    return OkState(0, "done");
}


OkState
RpcServerManager::addRemote(const std::string & name, const std::string &spec)
{
    OkState valid = validateName(name);
    if (valid.failed()) return valid;

    if (alreadyManaged(name, spec)) {
        return OkState(0, "already correct");
    }
    const NamedService *old = _rpcsrvmap.lookup(name);
    if (old != nullptr) {
        if (old->getSpec() !=  spec) {
            LOG(warning, "collision on remote add: name %s registered to %s locally, "
                "but another location broker wants it registered to %s",
                name.c_str(), old->getSpec().c_str(), spec.c_str());
            removeRemote(name, old->getSpec());
            return OkState(13, "registered, with different spec");
        }
        // was alright already, remove reservation
        _rpcsrvmap.removeReservation(name);
        return OkState(0, "already correct");
    }
    _rpcsrvmap.removeReservation(name);
    auto rpcsrv = std::make_unique<ManagedRpcServer>(name, spec, *this);
    ManagedRpcServer & rpcServer = *rpcsrv;
    _rpcsrvmap.addNew(std::move(rpcsrv));
    rpcServer.healthCheck();
    return OkState(0, "done");
}

OkState
RpcServerManager::remove(ManagedRpcServer *rpcsrv)
{
    const NamedService *td = _rpcsrvmap.lookup(rpcsrv->getName());
    if (td == rpcsrv) {
        return removeLocal(rpcsrv->getName(), rpcsrv->getSpec());
    } else {
        return OkState(1, "not currently registered");
    }
}


OkState
RpcServerManager::removeRemote(const std::string &name, const std::string &spec)
{
    const NamedService *old = _rpcsrvmap.lookup(name);
    if (old == nullptr) {
        // was alright already, remove any reservation too
        _rpcsrvmap.removeReservation(name);
        return OkState(0, "already done");
    }
    if (old->getSpec() != spec) {
        return OkState(1, "name registered, but with different spec");
    }
    std::unique_ptr<NamedService> td = _rpcsrvmap.remove(name);
    LOG_ASSERT(td.get() == old);
    return OkState(0, "done");
}

OkState
RpcServerManager::removeLocal(const std::string & name, const std::string &spec)
{
    const NamedService *td = _rpcsrvmap.lookup(name);
    if (td == nullptr) {
        // already removed, nop
        return OkState();
    }

    const RemoteSlobrok *partner = _exchanger.lookupPartner(name);
    if (partner != nullptr) {
        return OkState(13, "cannot unregister partner slobrok");
    }

    const ManagedRpcServer *rpcsrv = _rpcsrvmap.lookupManaged(name);
    if (rpcsrv == nullptr) {
        return OkState(13, "not a local rpcserver");
    }

    if (rpcsrv->getSpec() != spec) {
        // the client can probably ignore this "error"
        // or log it on level INFO?
        return OkState(1, fmt("name registered, but with different spec (%s)", rpcsrv->getSpec().c_str()));
    }
    auto tdUP = _rpcsrvmap.remove(name);
    LOG_ASSERT(tdUP.get() == rpcsrv);
    _exchanger.forwardRemove(name, spec);
    return OkState();
}


void
RpcServerManager::addManaged(ScriptCommand rdc)
{
    const std::string &name = rdc.name();
    const std::string &spec = rdc.spec();
    auto newRpcServer = std::make_unique<ManagedRpcServer>(name, spec, *this);
    ManagedRpcServer & rpcsrv = *newRpcServer;
    _rpcsrvmap.addNew(std::move(newRpcServer));
    for (size_t i = 0; i < _addManageds.size(); i++) {
        if (_addManageds[i].rpcsrv == nullptr) {
            _addManageds[i].rpcsrv = &rpcsrv;
            _addManageds[i].handler = std::move(rdc);
            rpcsrv.healthCheck();
            return;
        }
    }
    _addManageds.emplace_back(&rpcsrv, std::move(rdc));
    rpcsrv.healthCheck();
    return;
}



bool
RpcServerManager::alreadyManaged(const std::string &name, const std::string &spec)
{
    const ManagedRpcServer *rpcsrv = _rpcsrvmap.lookupManaged(name);
    if (rpcsrv != nullptr) {
        if (rpcsrv->getSpec() == spec) {
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
    std::vector<std::unique_ptr<ManagedRpcServer>> deleteAfterSwap;
    std::swap(deleteAfterSwap, _deleteList);
}


void
RpcServerManager::notifyFailedRpcSrv(ManagedRpcServer *rpcsrv, std::string errmsg)
{
    _env.countFailedHeartbeat();
    bool logged = false;
    const NamedService *old = _rpcsrvmap.lookup(rpcsrv->getName());
    if (old == rpcsrv) {
        old = _rpcsrvmap.remove(rpcsrv->getName()).release();
        LOG_ASSERT(old == rpcsrv);
        LOG(info, "managed server %s at %s failed: %s",
            rpcsrv->getName().c_str(), rpcsrv->getSpec().c_str(), errmsg.c_str());
        logged = true;
    }
    _exchanger.forwardRemove(rpcsrv->getName(), rpcsrv->getSpec());
    for (size_t i = 0; i < _addManageds.size(); ++i) {
        if (_addManageds[i].rpcsrv == rpcsrv) {
            _addManageds[i].handler
                .doneHandler(OkState(13, "failed check using listNames callback"));
            _addManageds[i].rpcsrv = 0;
            LOG(warning, "rpcserver %s at %s failed while trying to register",
                rpcsrv->getName().c_str(), rpcsrv->getSpec().c_str());
            logged = true;
        }
    }
    if (!logged) {
        LOG(warning, "unmanaged server %s at %s failed: %s",
            rpcsrv->getName().c_str(), rpcsrv->getSpec().c_str(), errmsg.c_str());
    }
    _deleteList.push_back(std::unique_ptr<ManagedRpcServer>(rpcsrv));
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
