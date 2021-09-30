// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/signalshutdown.h>
#include <vespa/fnet/transport.h>

#include <vespa/fastos/app.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("rpc_callback_server");

struct RPC : public FRT_Invokable
{
    void CallBack(FRT_RPCRequest *req);
    void Init(FRT_Supervisor *s);
};

void do_callback(FRT_RPCRequest *req) {
    FNET_Connection *conn = req->GetConnection();
    FRT_RPCRequest *cb = new FRT_RPCRequest();
    cb->SetMethodName(req->GetParams()->GetValue(0)._string._str);
    FRT_Supervisor::InvokeSync(conn->Owner(), conn, cb, 5.0);
    if(cb->IsError()) {
        printf("[error(%d): %s]\n",
               cb->GetErrorCode(),
               cb->GetErrorMessage());
    }
    cb->SubRef();
    req->Return();
}

void
RPC::CallBack(FRT_RPCRequest *req)
{
    req->Detach();
    std::thread(do_callback, req).detach();
}

void
RPC::Init(FRT_Supervisor *s)
{
    FRT_ReflectionBuilder rb(s);
    //-------------------------------------------------------------------
    rb.DefineMethod("callBack", "s", "",
                    FRT_METHOD(RPC::CallBack), this);
    //-------------------------------------------------------------------
}


class MyApp : public FastOS_Application
{
public:
    int Main() override;
};

int
MyApp::Main()
{
    FNET_SignalShutDown::hookSignals();
    if (_argc < 2) {
        printf("usage  : rpc_server <listenspec>\n");
        return 1;
    }
    RPC rpc;
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & supervisor = server.supervisor();
    rpc.Init(&supervisor);
    supervisor.Listen(_argv[1]);
    FNET_SignalShutDown ssd(*supervisor.GetTransport());
    server.supervisor().GetTransport()->WaitFinished();
    return 0;
}


int
main(int argc, char **argv)
{
    MyApp myapp;
    return myapp.Entry(argc, argv);
}
