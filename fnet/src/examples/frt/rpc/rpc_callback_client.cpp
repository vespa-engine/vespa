// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/frt.h>
#include <vespa/fastos/app.h>

struct RPC : public FRT_Invokable
{
    uint32_t invokeCnt;
    RPC() : invokeCnt(0) {}
    void Prod(FRT_RPCRequest *req);
    void Init(FRT_Supervisor *s);
};

void
RPC::Prod(FRT_RPCRequest *req)
{
    (void) req;
    ++invokeCnt;
}

void
RPC::Init(FRT_Supervisor *s)
{
    FRT_ReflectionBuilder rb(s);
    //-------------------------------------------------------------------
    rb.DefineMethod("prod", "", "", true,
                    FRT_METHOD(RPC::Prod), this);
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
    if (_argc < 2) {
        printf("usage  : rpc_server <connectspec>\n");
        return 1;
    }
    RPC rpc;
    FRT_Supervisor orb;
    rpc.Init(&orb);
    orb.Start();

    FRT_Target *target = orb.Get2WayTarget(_argv[1]);
    FRT_RPCRequest *req = orb.AllocRPCRequest();

    printf("invokeCnt: %d\n", rpc.invokeCnt);

    req->SetMethodName("callBack");
    req->GetParams()->AddString("prod");
    target->InvokeSync(req, 10.0);

    if(req->IsError()) {
        printf("[error(%d): %s]\n",
               req->GetErrorCode(),
               req->GetErrorMessage());
    }

    printf("invokeCnt: %d\n", rpc.invokeCnt);

    req = orb.AllocRPCRequest(req);
    req->SetMethodName("callBack");
    req->GetParams()->AddString("prod");
    target->InvokeSync(req, 10.0);

    if(req->IsError()) {
        printf("[error(%d): %s]\n",
               req->GetErrorCode(),
               req->GetErrorMessage());
    }

    printf("invokeCnt: %d\n", rpc.invokeCnt);

    req = orb.AllocRPCRequest(req);
    req->SetMethodName("callBack");
    req->GetParams()->AddString("prod");
    target->InvokeSync(req, 10.0);

    if(req->IsError()) {
        printf("[error(%d): %s]\n",
               req->GetErrorCode(),
               req->GetErrorMessage());
    }

    printf("invokeCnt: %d\n", rpc.invokeCnt);

    req->SubRef();
    target->SubRef();
    orb.ShutDown(true);
    return 0;
}


int
main(int argc, char **argv)
{
    MyApp myapp;
    return myapp.Entry(argc, argv);
}
