// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpchooks.h"
#include "cmdq.h"
#include "check-completion-handler.h"
#include "peer-check.h"
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>

#include <vespa/log/log.h>
LOG_SETUP(".rpchooks");

namespace config::sentinel {

RPCHooks::RPCHooks(CommandQueue &commands, FRT_Supervisor &supervisor)
  : _commands(commands),
    _orb(supervisor)
{
    initRPC(&_orb);
}

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
    rb.DefineMethod("sentinel.check.connectivity", "sii", "s",
                    FRT_METHOD(RPCHooks::rpc_checkConnectivity), this);
    rb.MethodDesc("check connectivity for peer sentinel");
    rb.ParamDesc("name", "Hostname of peer sentinel");
    rb.ParamDesc("port", "Port number of peer sentinel");
    rb.ParamDesc("timeout", "Timeout for check in milliseconds");
    rb.ReturnDesc("status", "Status (ok, bad, or unknown) for peer");
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

void
RPCHooks::rpc_checkConnectivity(FRT_RPCRequest *req)
{
    FRT_Values &args  = *req->GetParams();
    const char *hostname = args[0]._string._str;
    int portnum = args[1]._intval32;
    int timeout = args[2]._intval32;
    LOG(debug, "got checkConnectivity %s [port %d] timeout %d", hostname, portnum, timeout);
    req->Detach();
    auto & completionHandler = req->getStash().create<CheckCompletionHandler>(req);
    req->getStash().create<PeerCheck>(completionHandler, hostname, portnum, _orb, timeout);
}

} // namespace slobrok
