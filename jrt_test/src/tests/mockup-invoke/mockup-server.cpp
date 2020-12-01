// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/app.h>

class MockupServer : public FRT_Invokable
{
public:
    MockupServer(const MockupServer &) = delete;
    MockupServer &operator=(const MockupServer &) = delete;
    MockupServer(FRT_Supervisor *s)
    {
        FRT_ReflectionBuilder rb(s);
        //-------------------------------------------------------------------
        rb.DefineMethod("concat", "ss", "s",
                        FRT_METHOD(MockupServer::RPC_concat), this);
        rb.MethodDesc("Concatenate two strings");
        rb.ParamDesc("string1", "a string");
        rb.ParamDesc("string2", "another string");
        rb.ReturnDesc("ret", "the concatenation of string1 and string2");
        //-------------------------------------------------------------------
    }

    void RPC_concat(FRT_RPCRequest *req)
    {
        FRT_Values &params = *req->GetParams();
        FRT_Values &ret    = *req->GetReturn();

        uint32_t len = (params[0]._string._len +
                        params[1]._string._len);
        char *tmp = ret.AddString(len);
        strcpy(tmp, params[0]._string._str);
        strcat(tmp, params[1]._string._str);
    }
};


class App : public FastOS_Application
{
public:
    int Main() override;
};


int
App::Main()
{
    if (_argc < 2) {
        printf("usage: %s <listenspec>\n", _argv[0]);
        return 1;
    }
    fnet::frt::StandaloneFRT frt;
    MockupServer server(&frt.supervisor());
    frt.supervisor().Listen(_argv[1]);
    frt.supervisor().GetTransport()->WaitFinished();
    return 0;
}


int
main(int argc, char **argv)
{
  App myapp;
  return myapp.Entry(argc, argv);
}
