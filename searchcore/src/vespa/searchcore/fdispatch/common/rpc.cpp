// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "rpc.h"
#include "appcontext.h"

extern char FastS_VersionTag[];

FastS_RPC::FastS_RPC(FastS_AppContext *appCtx) :
   _appCtx(appCtx),
   _transport(),
   _supervisor(&_transport, _appCtx->GetThreadPool()),
   _sbregister(_supervisor, slobrok::ConfiguratorFactory("admin/slobrok.0"))
{
}

bool FastS_RPC::Start() {
    return _transport.Start(_appCtx->GetThreadPool());
}

void FastS_RPC::ShutDown() {
    _transport.ShutDown(true);
}

bool
FastS_RPC::Init(int port, const vespalib::string &myHeartbeatId)
{
    bool rc = true;

    char spec[4096];
    snprintf(spec, 4096, "tcp/%d", port);
    rc = rc && _supervisor.Listen(spec);
    if (rc) {
        FRT_ReflectionBuilder rb(&_supervisor);
        RegisterMethods(&rb);
        _sbregister.registerName(myHeartbeatId);
    }
    return rc;
}


void
FastS_RPC::RegisterMethods(FRT_ReflectionBuilder *rb)
{
    rb->DefineMethod("fs.admin.getNodeType", "", "s", true,
                     FRT_METHOD(FastS_RPC::RPC_GetNodeType), this);
    rb->MethodDesc("Get string indicating the node type");
    rb->ReturnDesc("type",  "node type");
    //---------------------------------------------------------------//
    rb->DefineMethod("fs.admin.getCompileInfo", "", "*", true,
                     FRT_METHOD(FastS_RPC::RPC_GetCompileInfo), this);
    rb->MethodDesc("Obtain compile info for this node");
    rb->ReturnDesc("info",  "any number of descriptive strings");
    //---------------------------------------------------------------//
}


void
FastS_RPC::RPC_GetCompileInfo(FRT_RPCRequest *req)
{
    FRT_Values &ret = *req->GetReturn();
    ret.AddString("using juniper (api version 2)");

#ifdef NO_MONITOR_LATENCY_CHECK
    ret.AddString("monitor latency check disabled");
#endif
#ifdef CUSTOM_TEST_SHUTDOWN
    ret.AddString("Win32: debug shutdown for memory leak detection enabled");
#endif
    ret.AddString("default transport is 'fnet'");

    const char *prefix = "version tag: ";
    uint32_t prefix_len = strlen(prefix);
    uint32_t len = prefix_len + strlen(FastS_VersionTag);
    if (len == prefix_len) {
        ret.AddString("version tag not available");
    } else {
        char *str = ret.AddString(len + 1);
        sprintf(str, "%s%s", prefix, FastS_VersionTag);
    }

    ret.AddString("fastos X current");
    ret.AddString(FNET_Info::GetFNETVersion());
}


void
FastS_RPC::RPC_GetNodeType_Proxy(FRT_RPCRequest *req)
{
    RPC_GetNodeType(req);
}
