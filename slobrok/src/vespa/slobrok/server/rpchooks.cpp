// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpchooks.h"
#include "ok_state.h"
#include "named_service.h"
#include "request_completion_handler.h"
#include "remote_slobrok.h"
#include "sbenv.h"
#include "rpcmirror.h"
#include <vespa/fnet/frt/require_capabilities.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/vespalib/component/vtag.h>

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.rpchooks");

namespace slobrok {

namespace {

class MetricsReport : public FNET_Task
{
    RPCHooks &_owner;

    void PerformTask() override {
        _owner.reportMetrics();
        Schedule(300.0);
    }
public:
    MetricsReport(FRT_Supervisor *orb, RPCHooks &owner)
        :  FNET_Task(orb->GetScheduler()),
           _owner(owner)
    {
        Schedule(0.0);
    }

    ~MetricsReport() override { Kill(); }
};

bool match(const char *name, const char *pattern) {
    LOG_ASSERT(name != nullptr);
    LOG_ASSERT(pattern != nullptr);
    while (*pattern != '\0') {
        if (*name == *pattern) {
            ++name;
            ++pattern;
        } else if (*pattern == '*') {
            ++pattern;
            while (*name != '/' && *name != '\0') {
                ++name;
            }
        } else {
            return false;
        }
    }
    return (*name == *pattern);
}

std::unique_ptr<FRT_RequireCapabilities> make_slobrok_capability_filter() {
    return FRT_RequireCapabilities::of(vespalib::net::tls::Capability::slobrok_api());
}

} // namespace <unnamed>

//-----------------------------------------------------------------------------

RPCHooks::RPCHooks(SBEnv &env)
    : _env(env),
      _globalHistory(env.globalHistory()),
      _localHistory(env.localHistory()),
      _cnts(Metrics::zero()),
      _m_reporter()
{
}


RPCHooks::~RPCHooks() = default;

void RPCHooks::reportMetrics() {
    EV_COUNT("heartbeats_failed", _cnts.heartBeatFails);
    EV_COUNT("register_reqs", _cnts.registerReqs);
    EV_COUNT("mirror_reqs", _cnts.mirrorReqs);
    EV_COUNT("wantadd_reqs", _cnts.wantAddReqs);
    EV_COUNT("doadd_reqs", _cnts.doAddReqs);
    EV_COUNT("doremove_reqs", _cnts.doRemoveReqs);
    EV_COUNT("admin_reqs", _cnts.adminReqs);
    EV_COUNT("other_reqs", _cnts.otherReqs);
}

void RPCHooks::initRPC(FRT_Supervisor *supervisor) {
    _m_reporter = std::make_unique<MetricsReport>(supervisor, *this);

    FRT_ReflectionBuilder rb(supervisor);

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.system.version", "", "s",
                    FRT_METHOD(RPCHooks::rpc_version), this);
    rb.MethodDesc("Get location broker version");
    rb.ReturnDesc("version", "version string");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.system.stop", "", "",
                    FRT_METHOD(RPCHooks::rpc_stop), this);
    rb.MethodDesc("Shut down the location broker application");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.listManagedRpcServers", "", "SS",
                    FRT_METHOD(RPCHooks::rpc_listManagedRpcServers), this);
    rb.MethodDesc("List all rpcservers managed by this location broker");
    rb.ReturnDesc("names", "Managed rpcserver names");
    rb.ReturnDesc("specs", "The connection specifications (in same order)");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.lookupManaged", "s", "ss",
                    FRT_METHOD(RPCHooks::rpc_lookupManaged), this);
    rb.MethodDesc("Lookup a specific rpcserver managed by this location broker");
    rb.ParamDesc("name", "Name of rpc server");
    rb.ReturnDesc("name", "Name of rpc server");
    rb.ReturnDesc("spec", "The connection specification");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.wantAdd", "sss", "is",
                    FRT_METHOD(RPCHooks::rpc_wantAdd), this);
    rb.MethodDesc("remote location broker wants to add a rpcserver");
    rb.ParamDesc("slobrok", "Name of remote location broker");
    rb.ParamDesc("name", "NamedService name to reserve");
    rb.ParamDesc("spec", "The connection specification");
    rb.ReturnDesc("denied", "non-zero if request was denied");
    rb.ReturnDesc("reason", "reason for denial");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.doAdd", "sss", "is",
                    FRT_METHOD(RPCHooks::rpc_doAdd), this);
    rb.MethodDesc("add rpcserver managed by remote location broker");
    rb.ParamDesc("slobrok", "Name of remote location broker");
    rb.ParamDesc("name", "NamedService name to add");
    rb.ParamDesc("spec", "The connection specification");
    rb.ReturnDesc("denied", "non-zero if request was denied");
    rb.ReturnDesc("reason", "reason for denial");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.doRemove", "sss", "is",
                    FRT_METHOD(RPCHooks::rpc_doRemove), this);
    rb.MethodDesc("remove rpcserver managed by remote location broker");
    rb.ParamDesc("slobrok", "Name of remote location broker");
    rb.ParamDesc("name", "NamedService name to remove");
    rb.ParamDesc("spec", "The connection specification");
    rb.ReturnDesc("denied", "non-zero if request was denied");
    rb.ReturnDesc("reason", "reason for denial");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.internal.fetchLocalView", "ii", "iSSSi",
                    FRT_METHOD(RPCHooks::rpc_fetchLocalView), this);
    rb.MethodDesc("Fetch or update peer mirror of local view");
    rb.ParamDesc("gencnt",  "generation already known by peer");
    rb.ParamDesc("timeout", "How many milliseconds to wait for changes"
                 "before returning if nothing has changed (max=10000)");

    rb.ReturnDesc("oldgen",  "Generation already known by peer");
    rb.ReturnDesc("removed", "Array of NamedService names to remove");
    rb.ReturnDesc("names",   "Array of NamedService names with new values");
    rb.ReturnDesc("specs",   "Array of connection specifications (same order)");
    rb.ReturnDesc("newgen",  "Generation count for new version of the map");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.callback.listNamesServed", "", "S",
                    FRT_METHOD(RPCHooks::rpc_listNamesServed), this);
    rb.MethodDesc("List rpcservers served");
    rb.ReturnDesc("names", "The rpcserver names this server wants to serve");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.admin.removePeer", "ss", "",
                    FRT_METHOD(RPCHooks::rpc_removePeer), this);
    rb.MethodDesc("stop syncing with other location broker");
    rb.ParamDesc("slobrok", "NamedService name of remote location broker");
    rb.ParamDesc("spec", "Connection specification of remote location broker");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.admin.addPeer", "ss", "",
                    FRT_METHOD(RPCHooks::rpc_addPeer), this);
    rb.MethodDesc("sync our information with other location broker");
    rb.ParamDesc("slobrok", "NamedService name of remote location broker");
    rb.ParamDesc("spec", "Connection specification of remote location broker");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.admin.listAllRpcServers", "", "SSS",
                    FRT_METHOD(RPCHooks::rpc_listAllRpcServers), this);
    rb.MethodDesc("List all known rpcservers");
    rb.ReturnDesc("names", "NamedService names");
    rb.ReturnDesc("specs", "The connection specifications (in same order)");
    rb.ReturnDesc("owners", "Corresponding names of managing location broker");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.unregisterRpcServer", "ss", "",
                    FRT_METHOD(RPCHooks::rpc_unregisterRpcServer), this);
    rb.MethodDesc("Unregister a rpcserver");
    rb.ParamDesc("name", "NamedService name");
    rb.ParamDesc("spec", "The connection specification");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.registerRpcServer", "ss", "",
                    FRT_METHOD(RPCHooks::rpc_registerRpcServer), this);
    rb.MethodDesc("Register a rpcserver");
    rb.ParamDesc("name", "NamedService name");
    rb.ParamDesc("spec", "The connection specification");
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.incremental.fetch", "ii", "iSSSi",
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
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
    rb.DefineMethod("slobrok.lookupRpcServer", "s", "SS",
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
    rb.RequestAccessFilter(make_slobrok_capability_filter());
    //-------------------------------------------------------------------------
}

void RPCHooks::rpc_listNamesServed(FRT_RPCRequest *req) {
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(1);
    dst.SetString(names, _env.mySpec().c_str());
    _cnts.otherReqs++;
    return;
}

void RPCHooks::rpc_registerRpcServer(FRT_RPCRequest *req) {
    FRT_Values &args   = *req->GetParams();
    const char *dName  = args[0]._string._str;
    const char *dSpec  = args[1]._string._str;
    LOG(debug, "RPC: invoked registerRpcServer(%s,%s)", dName, dSpec);
    _cnts.registerReqs++;
    ServiceMapping mapping{dName, dSpec};
    // can we say now, that this will fail?
    if (_env.consensusMap().wouldConflict(mapping)) {
        req->SetError(FRTE_RPC_METHOD_FAILED, "conflict detected");
        LOG(info, "cannot register %s at %s: conflict", dName, dSpec);
        return;
    }
    req->Detach();
    _env.localMonitorMap().addLocal(mapping, std::make_unique<RequestCompletionHandler>(req));
    return;
}

void RPCHooks::rpc_unregisterRpcServer(FRT_RPCRequest *req) {
    FRT_Values &args   = *req->GetParams();
    const char *dName  = args[0]._string._str;
    const char *dSpec  = args[1]._string._str;
    ServiceMapping mapping{dName, dSpec};
    _env.localMonitorMap().removeLocal(mapping);
    _env.exchangeManager().forwardRemove(dName, dSpec);
    LOG(debug, "unregisterRpcServer(%s,%s)", dName, dSpec);
    _cnts.otherReqs++;
    return;
}

void RPCHooks::rpc_addPeer(FRT_RPCRequest *req) {
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

void RPCHooks::rpc_removePeer(FRT_RPCRequest *req) {
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

void RPCHooks::rpc_wantAdd(FRT_RPCRequest *req) {
    FRT_Values &args   = *req->GetParams();
    const char *remsb  = args[0]._string._str;
    const char *dName  = args[1]._string._str;
    const char *dSpec  = args[2]._string._str;
    FRT_Values &retval = *req->GetReturn();
    ServiceMapping mapping{dName, dSpec};
    bool conflict = (
        _env.consensusMap().wouldConflict(mapping)
        ||
        _env.localMonitorMap().wouldConflict(mapping)
    );
    if (conflict) {
        retval.AddInt32(13);
        retval.AddString("conflict detected");
        req->SetError(FRTE_RPC_METHOD_FAILED, "conflict detected");
    } else {
        retval.AddInt32(0);
        retval.AddString("ok");
    }
    LOG(debug, "%s->wantAdd(%s,%s) %s",
        remsb, dName, dSpec, conflict ? "conflict" : "OK");
    _cnts.wantAddReqs++;
    return;
}

void RPCHooks::rpc_doRemove(FRT_RPCRequest *req) {
    FRT_Values &args   = *req->GetParams();
    const char *rname  = args[0]._string._str;
    const char *dname  = args[1]._string._str;
    const char *dspec  = args[2]._string._str;
    FRT_Values &retval = *req->GetReturn();
    ServiceMapping mapping{dname, dspec};
    _env.localMonitorMap().removeLocal(mapping);
    retval.AddInt32(0);
    retval.AddString("ok");
    LOG(debug, "%s->doRemove(%s,%s)", rname, dname, dspec);
    _cnts.doRemoveReqs++;
    return;
}

void RPCHooks::rpc_doAdd(FRT_RPCRequest *req) {
    FRT_Values &args   = *req->GetParams();
    const char *remsb  = args[0]._string._str;
    const char *dName  = args[1]._string._str;
    const char *dSpec  = args[2]._string._str;
    FRT_Values &retval = *req->GetReturn();
    ServiceMapping mapping{dName, dSpec};
    bool ok = true;
    if (_env.consensusMap().wouldConflict(mapping)) {
        retval.AddInt32(13);
        retval.AddString("conflict detected");
        req->SetError(FRTE_RPC_METHOD_FAILED, "conflict detected");
        ok = false;
    } else {
        retval.AddInt32(0);
        retval.AddString("ok");
    }
    LOG(debug, "%s->doAdd(%s,%s) %s",
        remsb, dName, dSpec, ok ? "OK" : "failed");
    _cnts.doAddReqs++;
    return;
}

void RPCHooks::rpc_lookupRpcServer(FRT_RPCRequest *req) {
    _cnts.otherReqs++;
    FRT_Values &args = *req->GetParams();
    const char *rpcserverPattern = args[0]._string._str;
    LOG(debug, "RPC: lookupRpcServers(%s)", rpcserverPattern);
    // fetch data:
    const auto & visible = _env.globalHistory();
    auto diff = visible.makeDiffFrom(0);
    std::vector<ServiceMapping> matches;
    for (const auto & entry : diff.updated) {
        if (match(entry.name.c_str(), rpcserverPattern)) {
            matches.push_back(entry);
        }
    }
    // fill return values:
    FRT_Values &dst = *req->GetReturn();
    size_t sz = matches.size();
    FRT_StringValue *names  = dst.AddStringArray(sz);
    FRT_StringValue *specs  = dst.AddStringArray(sz);
    size_t j = 0;
    for (const auto & entry : matches) {
        dst.SetString(&names[j], entry.name.c_str());
        dst.SetString(&specs[j], entry.spec.c_str());
        ++j;
    }
    // debug logging:
    if (sz < 1) {
        LOG(debug, "RPC: lookupRpcServers(%s) -> no match",
            rpcserverPattern);
    } else {
        LOG(debug, "RPC: lookupRpcServers(%s) -> %zu matches, first [%s,%s]",
            rpcserverPattern, sz,
            matches[0].name.c_str(), matches[0].spec.c_str());
    }
    return;
}

void RPCHooks::rpc_listManagedRpcServers(FRT_RPCRequest *req) {
    _cnts.adminReqs++;
    // TODO: use local history here
    const auto & visible = _env.globalHistory();
    auto diff = visible.makeDiffFrom(0);
    size_t sz = diff.updated.size();
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(sz);
    FRT_StringValue *specs = dst.AddStringArray(sz);
    size_t j = 0;
    for (const auto & entry : diff.updated) {
        dst.SetString(&names[j], entry.name.c_str());
        dst.SetString(&specs[j], entry.spec.c_str());
        ++j;
    }
    LOG(debug, "listManagedRpcServers -> %zu entries returned", sz);
    return;
}

void RPCHooks::rpc_lookupManaged(FRT_RPCRequest *req) {
    _cnts.adminReqs++;
    FRT_Values &args = *req->GetParams();
    const char *name = args[0]._string._str;
    LOG(debug, "RPC: lookupManaged(%s)", name);
    // TODO: use local history here
    const auto & visible = _env.globalHistory();
    auto diff = visible.makeDiffFrom(0);
    for (const auto & entry : diff.updated) {
        if (entry.name == name) {
            FRT_Values &dst = *req->GetReturn();
            dst.AddString(entry.name.c_str());
            dst.AddString(entry.spec.c_str());
            return;
        }
    }
    req->SetError(FRTE_RPC_METHOD_FAILED, "Not found");
    return;
}

void RPCHooks::rpc_listAllRpcServers(FRT_RPCRequest *req) {
    _cnts.adminReqs++;
    const auto & visible = _env.globalHistory();
    auto diff = visible.makeDiffFrom(0);
    size_t sz = diff.updated.size();
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names  = dst.AddStringArray(sz);
    FRT_StringValue *specs  = dst.AddStringArray(sz);
    FRT_StringValue *owner  = dst.AddStringArray(sz);
    size_t j = 0;
    for (const auto & entry : diff.updated) {
        dst.SetString(&names[j], entry.name.c_str());
        dst.SetString(&specs[j], entry.spec.c_str());
        dst.SetString(&owner[j], _env.mySpec().c_str());
        ++j;
    }
    LOG(debug, "listAllRpcServers -> %zu entries returned", sz);
    return;
}

void RPCHooks::rpc_incrementalFetch(FRT_RPCRequest *req) {
    _cnts.mirrorReqs++;
    FRT_Values &args = *req->GetParams();
    vespalib::GenCnt gencnt(args[0]._intval32);
    uint32_t msTimeout = args[1]._intval32;
    req->getStash().create<IncrementalFetch>(_env.getSupervisor(), req,
                                             _globalHistory, gencnt).invoke(msTimeout);
}

void RPCHooks::rpc_fetchLocalView(FRT_RPCRequest *req) {
    _cnts.mirrorReqs++;
    FRT_Values &args = *req->GetParams();
    vespalib::GenCnt gencnt(args[0]._intval32);
    uint32_t msTimeout = args[1]._intval32;
    req->getStash().create<IncrementalFetch>(_env.getSupervisor(), req,
                                             _localHistory, gencnt).invoke(msTimeout);
}

// System API methods
void RPCHooks::rpc_stop(FRT_RPCRequest *req) {
    _cnts.adminReqs++;
    (void) req;
    LOG(debug, "RPC stop command received, initiating shutdown");
    _env.shutdown();
}

void RPCHooks::rpc_version(FRT_RPCRequest *req) {
    _cnts.adminReqs++;
    std::string ver;

    char *s = vespalib::VersionTag;
    bool needdate = true;
    if (strncmp(vespalib::VersionTag, "V_", 2) == 0) {
        s += 2;
        do {
            while (strchr("0123456789", *s) != nullptr) {
                ver.append(s++, 1);
            }
            if (strncmp(s, "_RELEASE", 8) == 0) {
                needdate = false;
                break;
            }
            if (strncmp(s, "_RC", 3) == 0) {
                char *e = strchr(s, '-');
                if (e == nullptr) {
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
        if (e == nullptr) {
            ver.append(s);
        } else {
            ver.append(s, e - s);
        }
    }
    if (needdate) {
        ver.append("-");
        s = vespalib::VersionTagDate;
        char *e = strchr(s, '-');
        if (e == nullptr) {
            ver.append(s);
        } else {
            ver.append(s, e - s);
        }
    }
    LOG(debug, "RPC version: %s", ver.c_str());
    req->GetReturn()->AddString(ver.c_str());
    return;
}

} // namespace slobrok
