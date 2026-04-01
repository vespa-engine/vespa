// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/signalshutdown.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/signalhandler.h>

#include <vespa/log/log.h>
LOG_SETUP("rpc_server");

class RPCServer : public FRT_Invokable {
private:
    FRT_Supervisor* _supervisor;

    RPCServer(const RPCServer&);
    RPCServer& operator=(const RPCServer&);

public:
    RPCServer() : _supervisor(nullptr) {}
    void InitRPC(FRT_Supervisor* s);
    void RPC_concat(FRT_RPCRequest* req);
    void RPC_addFloat(FRT_RPCRequest* req);
    void RPC_addDouble(FRT_RPCRequest* req);
    int  main(int argc, char** argv);
};

void RPCServer::InitRPC(FRT_Supervisor* s) {
    FRT_ReflectionBuilder rb(s);
    //-------------------------------------------------------------------
    rb.DefineMethod("concat", "ss", "s", FRT_METHOD(RPCServer::RPC_concat), this);
    rb.MethodDesc("Concatenate two strings");
    rb.ParamDesc("string1", "a string");
    rb.ParamDesc("string2", "another string");
    rb.ReturnDesc("ret", "the concatenation of string1 and string2");
    //-------------------------------------------------------------------
    rb.DefineMethod("addFloat", "ff", "f", FRT_METHOD(RPCServer::RPC_addFloat), this);
    rb.MethodDesc("Add two floats");
    rb.ParamDesc("float1", "a float");
    rb.ParamDesc("float2", "another float");
    rb.ReturnDesc("ret", "float1 + float2");
    //-------------------------------------------------------------------
    rb.DefineMethod("addDouble", "dd", "d", FRT_METHOD(RPCServer::RPC_addDouble), this);
    rb.MethodDesc("Add two doubles");
    rb.ParamDesc("double1", "a double");
    rb.ParamDesc("double2", "another double");
    rb.ReturnDesc("ret", "double1 + double2");
    //-------------------------------------------------------------------
}

void RPCServer::RPC_concat(FRT_RPCRequest* req) {
    FRT_Values& params = *req->GetParams();
    FRT_Values& ret = *req->GetReturn();

    uint32_t len = (params[0]._string._len + params[1]._string._len);
    char*    tmp = ret.AddString(len);
    strcpy(tmp, params[0]._string._str);
    strcat(tmp, params[1]._string._str);
}

void RPCServer::RPC_addFloat(FRT_RPCRequest* req) {
    FRT_Values& params = *req->GetParams();
    FRT_Values& ret = *req->GetReturn();

    ret.AddFloat(params[0]._float + params[1]._float);
}

void RPCServer::RPC_addDouble(FRT_RPCRequest* req) {
    FRT_Values& params = *req->GetParams();
    FRT_Values& ret = *req->GetReturn();

    ret.AddDouble(params[0]._double + params[1]._double);
}

int RPCServer::main(int argc, char** argv) {
    FNET_SignalShutDown::hookSignals();
    if (argc < 2) {
        printf("usage  : rpc_server <listenspec>\n");
        return 1;
    }

    fnet::frt::StandaloneFRT server;
    _supervisor = &server.supervisor();
    InitRPC(_supervisor);
    _supervisor->Listen(argv[1]);
    FNET_SignalShutDown ssd(*_supervisor->GetTransport());
    server.supervisor().GetTransport()->WaitFinished();
    return 0;
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    RPCServer server;
    return server.main(argc, argv);
}
