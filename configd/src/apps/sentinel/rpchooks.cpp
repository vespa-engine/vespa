// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpchooks.h"
#include "cmdq.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP(".rpchooks");

namespace config::sentinel {

RPCHooks::~RPCHooks() = default;


void
RPCHooks::initRPC(FRT_Supervisor *supervisor)
{
    FRT_ReflectionBuilder rb(supervisor);

    //-------------------------------------------------------------------------
    rb.DefineMethod("sentinel.ls", "", "s",
                    FRT_METHOD(RPCHooks::rpc_listServices), this);
    rb.MethodDesc("list services");
    rb.ReturnDesc("status", "Status for services");
    //-------------------------------------------------------------------------
    rb.DefineMethod("sentinel.service.restart", "s", "",
                    FRT_METHOD(RPCHooks::rpc_restartService), this);
    rb.MethodDesc("restart a service");
    //-------------------------------------------------------------------------
    rb.DefineMethod("sentinel.service.stop", "s", "",
                    FRT_METHOD(RPCHooks::rpc_stopService), this);
    rb.MethodDesc("stop a service");
    //-------------------------------------------------------------------------
    rb.DefineMethod("sentinel.service.start", "s", "",
                    FRT_METHOD(RPCHooks::rpc_startService), this);
    rb.MethodDesc("start a service");
    //-------------------------------------------------------------------------
}

void
RPCHooks::rpc_listServices(FRT_RPCRequest *req)
{
    LOG(debug, "got listservices");
    req->Detach();
    _commands.enqueue(std::make_unique<Cmd>(req, Cmd::LIST));
}

void
RPCHooks::rpc_restartService(FRT_RPCRequest *req)
{
    FRT_Values &args  = *req->GetParams();
    const char *srvNM = args[0]._string._str;
    LOG(debug, "got restartservice '%s'", srvNM);
    req->Detach();
    _commands.enqueue(std::make_unique<Cmd>(req, Cmd::RESTART, srvNM));
}

void
RPCHooks::rpc_stopService(FRT_RPCRequest *req)
{
    FRT_Values &args  = *req->GetParams();
    const char *srvNM = args[0]._string._str;
    LOG(debug, "got stopservice '%s'", srvNM);
    req->Detach();
    _commands.enqueue(std::make_unique<Cmd>(req, Cmd::STOP, srvNM));
}

void
RPCHooks::rpc_startService(FRT_RPCRequest *req)
{
    FRT_Values &args  = *req->GetParams();
    const char *srvNM = args[0]._string._str;
    LOG(debug, "got startservice '%s'", srvNM);
    req->Detach();
    _commands.enqueue(std::make_unique<Cmd>(req, Cmd::START, srvNM));
}

} // namespace slobrok
