// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fastos/app.h>

#include <vespa/log/log.h>
LOG_SETUP("rpc_client");

class RPCClient : public FastOS_Application
{
public:
    int Main() override;
};

int
RPCClient::Main()
{
    if (_argc < 2) {
        printf("usage  : rpc_client <connectspec>\n");
        return 1;
    }
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & supervisor = server.supervisor();

    FRT_Target *target = supervisor.GetTarget(_argv[1]);

    const char *str1 = "abc";
    const char *str2 = "def";
    float float1     =  20.5;
    float float2     =  60.5;
    double double1   =  25.5;
    double double2   =   5.5;

    fprintf(stdout, "\nTesting concat method\n");
    FRT_RPCRequest *req = supervisor.AllocRPCRequest();
    req->SetMethodName("concat");
    req->GetParams()->AddString(str1);
    req->GetParams()->AddString(str2);
    target->InvokeSync(req, 5.0);
    if (req->GetErrorCode() == FRTE_NO_ERROR) {
        fprintf(stdout, "%s + %s = %s\n", str1, str2,
                req->GetReturn()->GetValue(0)._string._str);
    } else {
        fprintf(stdout, "error(%d): %s\n",
                req->GetErrorCode(),
                req->GetErrorMessage());
    }

    fprintf(stdout, "\nTesting addFloat method\n");
    req->SubRef();
    req = supervisor.AllocRPCRequest();
    req->SetMethodName("addFloat");
    req->GetParams()->AddFloat(float1);
    req->GetParams()->AddFloat(float2);
    target->InvokeSync(req, 5.0);
    if (req->GetErrorCode() == FRTE_NO_ERROR) {
        fprintf(stdout, "%f + %f = %f\n", float1, float2,
                req->GetReturn()->GetValue(0)._float);
    } else {
        fprintf(stdout, "error(%d): %s\n",
                req->GetErrorCode(),
                req->GetErrorMessage());
    }

    fprintf(stdout, "\nTesting addDouble method\n");
    req->SubRef();
    req = supervisor.AllocRPCRequest();
    req->SetMethodName("addDouble");
    req->GetParams()->AddDouble(double1);
    req->GetParams()->AddDouble(double2);
    target->InvokeSync(req, 5.0);
    if (req->GetErrorCode() == FRTE_NO_ERROR) {
        fprintf(stdout, "%f + %f = %f\n", double1, double2,
                req->GetReturn()->GetValue(0)._double);
    } else {
        fprintf(stdout, "error(%d): %s\n",
                req->GetErrorCode(),
                req->GetErrorMessage());
    }

    req->SubRef();
    target->SubRef();
    return 0;
}


int
main(int argc, char **argv)
{
    RPCClient myapp;
    return myapp.Entry(argc, argv);
}
