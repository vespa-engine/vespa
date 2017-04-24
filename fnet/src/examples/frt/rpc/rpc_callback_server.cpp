// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/frt.h>
#include <vespa/fastos/app.h>

#include <vespa/log/log.h>
LOG_SETUP("rpc_callback_server");


struct RPC : public FRT_Invokable
{
    void CallBack(FRT_RPCRequest *req);
    void Init(FRT_Supervisor *s);
};

void
RPC::CallBack(FRT_RPCRequest *req)
{
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
}

void
RPC::Init(FRT_Supervisor *s)
{
    FRT_ReflectionBuilder rb(s);
    //-------------------------------------------------------------------
    rb.DefineMethod("callBack", "s", "", false,
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
    FRT_Supervisor orb;
    rpc.Init(&orb);
    orb.Listen(_argv[1]);
    FNET_SignalShutDown ssd(*orb.GetTransport());
    orb.Main();
    return 0;
}


int
main(int argc, char **argv)
{
    MyApp myapp;
    return myapp.Entry(argc, argv);
}
