// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/fnet/frt/frt.h>
#include "rpchooks.h"
#include "ok_state.h"
#include "named_service.h"
#include "rpc_server_map.h"
#include "rpc_server_manager.h"
#include "remote_slobrok.h"
#include "sbenv.h"
#include "visible_map.h"
#include "rpcmirror.h"

#include <vespa/log/log.h>

LOG_SETUP(".rpchooks");

namespace slobrok {

namespace {



class MetricsReport : public FNET_Task
{
    RPCHooks &_owner;

    void PerformTask() {
        _owner.reportMetrics();
        Schedule(300.0);
    }
public:
    MetricsReport(FRT_Supervisor *orb,
                  RPCHooks &owner)
        :  FNET_Task(orb->GetScheduler()),
           _owner(owner)
    {
        Schedule(0.0);
    }

    virtual ~MetricsReport() { Kill(); }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------

RPCHooks::RPCHooks(SBEnv &env,
                   RpcServerMap& rpcsrvmap,
                   RpcServerManager& rpcsrvman)
    : _env(env), _rpcsrvmap(rpcsrvmap), _rpcsrvmanager(rpcsrvman),
      _cnts{0, 0, 0, 0, 0, 0, 0},
      _m_reporter(NULL)
{
}


RPCHooks::~RPCHooks()
{
    delete _m_reporter;
}

void
RPCHooks::reportMetrics()
{
    EV_COUNT("register_reqs", _cnts.registerReqs);
    EV_COUNT("mirror_reqs", _cnts.mirrorReqs);
    EV_COUNT("wantadd_reqs", _cnts.wantAddReqs);
    EV_COUNT("doadd_reqs", _cnts.doAddReqs);
    EV_COUNT("doremove_reqs", _cnts.doRemoveReqs);
    EV_COUNT("admin_reqs", _cnts.adminReqs);
    EV_COUNT("other_reqs", _cnts.otherReqs);
}


void
RPCHooks::initRPC(FRT_Supervisor *supervisor)
{
    _m_reporter = new MetricsReport(supervisor, *this);

    FRT_ReflectionBuilder rb(supervisor);

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.system.resume", "", "", true,
                    FRT_METHOD(RPCHooks::rpc_resume), this);
    rb.MethodDesc("Enable something - currently NOP");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.system.suspend", "", "", true,
                    FRT_METHOD(RPCHooks::rpc_suspend), this);
    rb.MethodDesc("Disable something - currently NOP");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.system.version", "", "s", true,
                    FRT_METHOD(RPCHooks::rpc_version), this);
    rb.MethodDesc("Get location broker version");
    rb.ReturnDesc("version", "version string");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.system.stop", "", "", true,
                    FRT_METHOD(RPCHooks::rpc_stop), this);
    rb.MethodDesc("Shut down the location broker application");
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.listManagedRpcServers", "", "SS", true,
                    FRT_METHOD(RPCHooks::rpc_listManagedRpcServers), this);
    rb.MethodDesc("List all rpcservers managed by this location broker");
    rb.ReturnDesc("names", "Managed rpcserver names");
    rb.ReturnDesc("specs", "The connection specifications (in same order)");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.lookupManaged", "s", "ss", true,
                    FRT_METHOD(RPCHooks::rpc_lookupManaged), this);
    rb.MethodDesc("Lookup a specific rpcserver managed by this location broker");
    rb.ParamDesc("name", "Name of rpc server");
    rb.ReturnDesc("name", "Name of rpc server");
    rb.ReturnDesc("spec", "The connection specification");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.wantAdd", "sss", "is", true,
                    FRT_METHOD(RPCHooks::rpc_wantAdd), this);
    rb.MethodDesc("remote location broker wants to add a rpcserver");
    rb.ParamDesc("slobrok", "Name of remote location broker");
    rb.ParamDesc("name", "NamedService name to reserve");
    rb.ParamDesc("spec", "The connection specification");
    rb.ReturnDesc("denied", "non-zero if request was denied");
    rb.ReturnDesc("reason", "reason for denial");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.doAdd", "sss", "is", true,
                    FRT_METHOD(RPCHooks::rpc_doAdd), this);
    rb.MethodDesc("add rpcserver managed by remote location broker");
    rb.ParamDesc("slobrok", "Name of remote location broker");
    rb.ParamDesc("name", "NamedService name to add");
    rb.ParamDesc("spec", "The connection specification");
    rb.ReturnDesc("denied", "non-zero if request was denied");
    rb.ReturnDesc("reason", "reason for denial");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.doRemove", "sss", "is", true,
                    FRT_METHOD(RPCHooks::rpc_doRemove), this);
    rb.MethodDesc("remove rpcserver managed by remote location broker");
    rb.ParamDesc("slobrok", "Name of remote location broker");
    rb.ParamDesc("name", "NamedService name to remove");
    rb.ParamDesc("spec", "The connection specification");
    rb.ReturnDesc("denied", "non-zero if request was denied");
    rb.ReturnDesc("reason", "reason for denial");
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.listNamesServed", "", "S", true,
                    FRT_METHOD(RPCHooks::rpc_listNamesServed), this);
    rb.MethodDesc("List rpcservers served");
    rb.ReturnDesc("names", "The rpcserver names this server wants to serve");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.notifyUnregistered", "s", "", true,
                    FRT_METHOD(RPCHooks::rpc_notifyUnregistered), this);
    rb.MethodDesc("Notify a server about removed registration");
    rb.ParamDesc("name", "NamedService name");
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.admin.removePeer", "ss", "", true,
                    FRT_METHOD(RPCHooks::rpc_removePeer), this);
    rb.MethodDesc("stop syncing with other location broker");
    rb.ParamDesc("slobrok", "NamedService name of remote location broker");
    rb.ParamDesc("spec", "Connection specification of remote location broker");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.admin.addPeer", "ss", "", true,
                    FRT_METHOD(RPCHooks::rpc_addPeer), this);
    rb.MethodDesc("sync our information with other location broker");
    rb.ParamDesc("slobrok", "NamedService name of remote location broker");
    rb.ParamDesc("spec", "Connection specification of remote location broker");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.admin.listAllRpcServers", "", "SSS", true,
                    FRT_METHOD(RPCHooks::rpc_listAllRpcServers), this);
    rb.MethodDesc("List all known rpcservers");
    rb.ReturnDesc("names", "NamedService names");
    rb.ReturnDesc("specs", "The connection specifications (in same order)");
    rb.ReturnDesc("owners", "Corresponding names of managing location broker");
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.unregisterRpcServer", "ss", "", true,
                    FRT_METHOD(RPCHooks::rpc_unregisterRpcServer), this);
    rb.MethodDesc("Unregister a rpcserver");
    rb.ParamDesc("name", "NamedService name");
    rb.ParamDesc("spec", "The connection specification");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.registerRpcServer", "ss", "", true,
                    FRT_METHOD(RPCHooks::rpc_registerRpcServer), this);
    rb.MethodDesc("Register a rpcserver");
    rb.ParamDesc("name", "NamedService name");
    rb.ParamDesc("spec", "The connection specification");
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.mirror.fetch", "ii", "SSi", true,
                    FRT_METHOD(RPCHooks::rpc_mirrorFetch), this);
    rb.MethodDesc("Fetch or update mirror of name to spec map");
    rb.ParamDesc("gencnt", "generation already known by client");
    rb.ParamDesc("timeout", "How many milliseconds to wait for changes"
                 "before returning if nothing has changed (max=10000)");
    rb.ReturnDesc("names", "Array of NamedService names");
    rb.ReturnDesc("specs", "Array of connection specifications (same order)");
    rb.ReturnDesc("newgen", "Generation count for new version of the map");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.incremental.fetch", "ii", "iSSSi", true,
                    FRT_METHOD(RPCHooks::rpc_incrementalFetch), this);
    rb.MethodDesc("Fetch or update mirror of name to spec map");
    rb.ParamDesc("gencnt",  "generation already known by client");
    rb.ParamDesc("timeout", "How many milliseconds to wait for changes"
                 "before returning if nothing has changed (max=10000)");

    rb.ReturnDesc("oldgen",  "diff from generation already known by client");
    rb.ReturnDesc("removed", "Array of NamedService names to remove");
    rb.ReturnDesc("names",   "Array of NamedService names with new values");
    rb.ReturnDesc("specs",   "Array of connection specifications (same order)");
    rb.ReturnDesc("newgen",  "Generation count for new version of the map");
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.lookupRpcServer", "s", "SS", true,
                    FRT_METHOD(RPCHooks::rpc_lookupRpcServer), this);
    rb.MethodDesc("Look up rpcservers");
    rb.ParamDesc("pattern", "The pattern of the rpcservers to lookup.\n"
                 "                 "
                 "The pattern may contain * characters to match a component.\n"
                 "                 "
                 "Components are delimited by / characters.\n"
                 "                 "
                 "There is no way to match an arbitrary number of components\n"
                 "                 "
                 "or to match just a part of a component."
                 );
    rb.ReturnDesc("names", "The rpcserver names matching pattern");
    rb.ReturnDesc("specs", "The connection specifications (in same order)");
    //-------------------------------------------------------------------------
}


void
RPCHooks::rpc_listNamesServed(FRT_RPCRequest *req)
{
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(1);
    dst.SetString(names, _env.mySpec());
    _cnts.otherReqs++;
    return;
}

void
RPCHooks::rpc_notifyUnregistered(FRT_RPCRequest *req)
{
    FRT_Values &args   = *req->GetParams();
    const char *dName  = args[0]._string._str;
    LOG(warning, "got notifyUnregistered '%s'", dName);
    _cnts.otherReqs++;
    return;
}


void
RPCHooks::rpc_registerRpcServer(FRT_RPCRequest *req)
{
    FRT_Values &args   = *req->GetParams();
    const char *dName  = args[0]._string._str;
    const char *dSpec  = args[1]._string._str;

    LOG(debug, "RPC: invoked registerRpcServer(%s,%s)", dName, dSpec);
    _cnts.registerReqs++;

    // is this already OK?
    if (_rpcsrvmanager.alreadyManaged(dName, dSpec)) {
        LOG(debug, "registerRpcServer(%s,%s) OK, already managed",
            dName, dSpec);
        return;
    }
    // can we say now, that this will fail?
    OkState state = _rpcsrvmanager.addMyReservation(dName, dSpec);
    if (state.failed()) {
        req->SetError(FRTE_RPC_METHOD_FAILED, state.errorMsg.c_str());
        LOG(info, "cannot register %s at %s: %s", dName, dSpec, state.errorMsg.c_str());
        return;
    }
    // need to actually setup management and decide result later
    req->Detach();
    RegRpcSrvCommand completer
        = RegRpcSrvCommand::makeRegRpcSrvCmd(_env, dName, dSpec, req);
    completer.doRequest();
}

void
RPCHooks::rpc_unregisterRpcServer(FRT_RPCRequest *req)
{
    FRT_Values &args   = *req->GetParams();
    const char *dName  = args[0]._string._str;
    const char *dSpec  = args[1]._string._str;
    OkState state = _rpcsrvmanager.removeLocal(dName, dSpec);
    if (state.failed()) {
        req->SetError(FRTE_RPC_METHOD_FAILED, state.errorMsg.c_str());
    }
    LOG(debug, "unregisterRpcServer(%s,%s) %s: %s",
        dName, dSpec,
        state.ok() ? "OK" : "failed",
        state.errorMsg.c_str());
    _cnts.otherReqs++;
    return;
}


void
RPCHooks::rpc_addPeer(FRT_RPCRequest *req)
{
    FRT_Values &args = *req->GetParams();
    const char *remslobrok = args[0]._string._str;
    const char *remsbspec  = args[1]._string._str;

    OkState ok = _env.addPeer(remslobrok, remsbspec);
    if (ok.failed()) {
        req->SetError(FRTE_RPC_METHOD_FAILED, ok.errorMsg.c_str());
    }
    LOG(debug, "addPeer(%s,%s) %s: %s",
        remslobrok, remsbspec,
        ok.ok() ? "OK" : "failed",
        ok.errorMsg.c_str());
    _cnts.adminReqs++;
    return;
}


void
RPCHooks::rpc_removePeer(FRT_RPCRequest *req)
{
    FRT_Values &args = *req->GetParams();
    const char *remslobrok = args[0]._string._str;
    const char *remsbspec  = args[1]._string._str;

    OkState ok = _env.removePeer(remslobrok, remsbspec);
    if (ok.failed()) {
        req->SetError(FRTE_RPC_METHOD_FAILED, ok.errorMsg.c_str());
    }
    LOG(debug, "removePeer(%s,%s) %s: %s",
        remslobrok, remsbspec,
        ok.ok() ? "OK" : "failed",
        ok.errorMsg.c_str());
    _cnts.adminReqs++;
    return;
}


void
RPCHooks::rpc_wantAdd(FRT_RPCRequest *req)
{
    FRT_Values &args   = *req->GetParams();
    const char *remsb  = args[0]._string._str;
    const char *dName  = args[1]._string._str;
    const char *dSpec  = args[2]._string._str;
    FRT_Values &retval = *req->GetReturn();
    OkState state = _rpcsrvmanager.addRemReservation(remsb, dName, dSpec);
    if (state.failed()) {
        req->SetError(FRTE_RPC_METHOD_FAILED, state.errorMsg.c_str());
    }
    retval.AddInt32(state.errorCode);
    retval.AddString(state.errorMsg.c_str());
    LOG(debug, "%s->wantAdd(%s,%s) %s: %s",
        remsb, dName, dSpec,
        state.ok() ? "OK" : "failed",
        state.errorMsg.c_str());
    _cnts.wantAddReqs++;
    return;
}


void
RPCHooks::rpc_doRemove(FRT_RPCRequest *req)
{
    FRT_Values &args   = *req->GetParams();
    const char *rname  = args[0]._string._str;
    const char *dname  = args[1]._string._str;
    const char *dspec  = args[2]._string._str;
    FRT_Values &retval = *req->GetReturn();
    OkState state = _rpcsrvmanager.removeRemote(dname, dspec);
    retval.AddInt32(state.errorCode);
    retval.AddString(state.errorMsg.c_str());
    if (state.errorCode > 1) {
        req->SetError(FRTE_RPC_METHOD_FAILED, state.errorMsg.c_str());
    }
    LOG(debug, "%s->doRemove(%s,%s) %s: %s",
        rname, dname, dspec,
        state.ok() ? "OK" : "failed",
        state.errorMsg.c_str());
    _cnts.doRemoveReqs++;
    return;
}

void
RPCHooks::rpc_doAdd(FRT_RPCRequest *req)
{
    FRT_Values &args   = *req->GetParams();
    const char *rname  = args[0]._string._str;
    const char *dname  = args[1]._string._str;
    const char *dspec  = args[2]._string._str;
    FRT_Values &retval = *req->GetReturn();
    OkState state = _rpcsrvmanager.addRemote(dname, dspec);
    retval.AddInt32(state.errorCode);
    retval.AddString(state.errorMsg.c_str());
    if (state.errorCode > 1) {
        req->SetError(FRTE_RPC_METHOD_FAILED, state.errorMsg.c_str());
    }
    LOG(debug, "%s->doAdd(%s,%s) %s: %s",
        rname, dname, dspec,
        state.ok() ? "OK" : "failed",
        state.errorMsg.c_str());
    _cnts.doAddReqs++;
    return;
}


void
RPCHooks::rpc_lookupRpcServer(FRT_RPCRequest *req)
{
    _cnts.otherReqs++;
    FRT_Values &args = *req->GetParams();
    const char *rpcserverPattern = args[0]._string._str;
    LOG(debug, "RPC: lookupRpcServers(%s)", rpcserverPattern);
    std::vector<const NamedService *> rpcsrvlist =
        _rpcsrvmap.lookupPattern(rpcserverPattern);
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(rpcsrvlist.size());
    FRT_StringValue *specs = dst.AddStringArray(rpcsrvlist.size());
    for (uint32_t i = 0; i < rpcsrvlist.size(); ++i) {
        dst.SetString(&names[i], rpcsrvlist[i]->getName());
        dst.SetString(&specs[i], rpcsrvlist[i]->getSpec());
    }
    if (rpcsrvlist.size() < 1) {
        LOG(debug, "RPC: lookupRpcServers(%s) -> no match",
            rpcserverPattern);
    } else {
        LOG(debug, "RPC: lookupRpcServers(%s) -> %u matches, first [%s,%s]",
            rpcserverPattern, (unsigned int)rpcsrvlist.size(),
            rpcsrvlist[0]->getName(), rpcsrvlist[0]->getSpec());
    }
    return;
}


void
RPCHooks::rpc_listManagedRpcServers(FRT_RPCRequest *req)
{
    _cnts.adminReqs++;
    std::vector<const NamedService *> rpcsrvlist = _rpcsrvmap.allManaged();
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(rpcsrvlist.size());
    FRT_StringValue *specs = dst.AddStringArray(rpcsrvlist.size());
    for (uint32_t i = 0; i < rpcsrvlist.size(); ++i) {
        dst.SetString(&names[i], rpcsrvlist[i]->getName());
        dst.SetString(&specs[i], rpcsrvlist[i]->getSpec());
    }
    if (rpcsrvlist.size() < 1) {
        LOG(debug, "RPC: listManagedRpcServers() -> 0 managed");
    } else {
        LOG(debug, "RPC: listManagedRpcServers() -> %u managed, first [%s,%s]",
            (unsigned int)rpcsrvlist.size(),
            rpcsrvlist[0]->getName(), rpcsrvlist[0]->getSpec());
    }
    return;
}

void
RPCHooks::rpc_lookupManaged(FRT_RPCRequest *req)
{
    _cnts.adminReqs++;
    FRT_Values &args = *req->GetParams();
    const char *name = args[0]._string._str;
    LOG(debug, "RPC: lookupManaged(%s)", name);
    ManagedRpcServer *found = _rpcsrvmap.lookupManaged(name);

    if (found == NULL) {
        req->SetError(FRTE_RPC_METHOD_FAILED, "Not found");
    } else {
        FRT_Values &dst = *req->GetReturn();
        dst.AddString(found->getName());
        dst.AddString(found->getSpec());
    }
    return;
}

void
RPCHooks::rpc_listAllRpcServers(FRT_RPCRequest *req)
{
    _cnts.adminReqs++;

    std::vector<const NamedService *> mrpcsrvlist = _rpcsrvmap.allManaged();

    FRT_Values &dst = *req->GetReturn();
    size_t sz = mrpcsrvlist.size();
    FRT_StringValue *names  = dst.AddStringArray(sz);
    FRT_StringValue *specs  = dst.AddStringArray(sz);
    FRT_StringValue *owner  = dst.AddStringArray(sz);

    int j = 0;
    for (uint32_t i = 0; i < mrpcsrvlist.size(); ++i, ++j) {
        dst.SetString(&names[j], mrpcsrvlist[i]->getName());
        dst.SetString(&specs[j], mrpcsrvlist[i]->getSpec());
        dst.SetString(&owner[j], _env.mySpec());
    }

    if (sz > 0) {
        LOG(debug, "listManagedRpcServers -> %u, last [%s,%s,%s]",
            (unsigned int)mrpcsrvlist.size(),
            dst[0]._string_array._pt[sz-1]._str,
            dst[1]._string_array._pt[sz-1]._str,
            dst[2]._string_array._pt[sz-1]._str);
    } else {
        LOG(debug, "listManagedRpcServers -> %u", (unsigned int) mrpcsrvlist.size());
    }
    return;

}


void
RPCHooks::rpc_mirrorFetch(FRT_RPCRequest *req)
{
    _cnts.mirrorReqs++;
    FRT_Values &args = *req->GetParams();

    vespalib::GenCnt gencnt(args[0]._intval32);
    uint32_t msTimeout = args[1]._intval32;

    (new (req->getStash())
     MirrorFetch(_env.getSupervisor(), req, _rpcsrvmap.visibleMap(), gencnt))->invoke(msTimeout);
}

void
RPCHooks::rpc_incrementalFetch(FRT_RPCRequest *req)
{
    _cnts.mirrorReqs++;
    FRT_Values &args = *req->GetParams();

    vespalib::GenCnt gencnt(args[0]._intval32);
    uint32_t msTimeout = args[1]._intval32;

    (new (req->getStash())
     IncrementalFetch(_env.getSupervisor(), req, _rpcsrvmap.visibleMap(), gencnt))->invoke(msTimeout);
}


// System API methods
void
RPCHooks::rpc_stop(FRT_RPCRequest *req)
{
    _cnts.adminReqs++;
    (void) req;
    LOG(debug, "RPC stop command received, initiating shutdown");
    _env.shutdown();
}


void
RPCHooks::rpc_version(FRT_RPCRequest *req)
{
    _cnts.adminReqs++;
    std::string ver;

    char *s = vespalib::VersionTag;
    bool needdate = true;
    if (strncmp(vespalib::VersionTag, "V_", 2) == 0) {
        s += 2;
        do {
            while (strchr("0123456789", *s) != NULL) {
                ver.append(s++, 1);
            }
            if (strncmp(s, "_RELEASE", 8) == 0) {
                needdate = false;
                break;
            }
            if (strncmp(s, "_RC", 3) == 0) {
                char *e = strchr(s, '-');
                if (e == NULL) {
                    ver.append(s);
                } else {
                    ver.append(s, e - s);
                }
                needdate = false;
                break;
            }
            if (*s == '_' && strchr("0123456789", *++s)) {
                ver.append(".");
            } else {
                break;
            }
        } while (*s && *s != '-');
    } else {
        char *e = strchr(s, '-');
        if (e == NULL) {
            ver.append(s);
        } else {
            ver.append(s, e - s);
        }
    }
    if (needdate) {
        ver.append("-");
        s = vespalib::VersionTagDate;
        char *e = strchr(s, '-');
        if (e == NULL) {
            ver.append(s);
        } else {
            ver.append(s, e - s);
        }
    }
    LOG(debug, "RPC version: %s", ver.c_str());

    req->GetReturn()->AddString(ver.c_str());
    return;
}


void
RPCHooks::rpc_suspend(FRT_RPCRequest *req)
{
    _cnts.adminReqs++;
    req->SetError(FRTE_RPC_METHOD_FAILED, "not implemented");
    LOG(debug, "RPC suspend command received (ignored)");
}


void
RPCHooks::rpc_resume(FRT_RPCRequest *req)
{
    _cnts.adminReqs++;
    // XXX always ignored
    (void) req;
    LOG(debug, "RPC resume command received (ignored)");
}

} // namespace slobrok
