// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/app.h>

class Server : public FRT_Invokable
{
private:
    Server(const Server &);
    Server &operator=(const Server &);

public:
    Server(FRT_Supervisor *s)
    {
        FRT_ReflectionBuilder rb(s);
        //---------------------------------------------------------------------
        rb.DefineMethod("inc", "i", "i",
                        FRT_METHOD(Server::rpc_inc), this);
        rb.MethodDesc("Increase an integer value");
        rb.ParamDesc("value", "initial value");
        rb.ReturnDesc("result", "value + 1");
        //---------------------------------------------------------------------
        rb.DefineMethod("blob", "x", "x",
                        FRT_METHOD(Server::rpc_blob), this);
        rb.MethodDesc("Send a copy of a blob back to the client");
        rb.ParamDesc("blob", "the original blob");
        rb.ReturnDesc("blob", "a copy of the original blob");
        //---------------------------------------------------------------------
        rb.DefineMethod("test", "iib", "i",
                        FRT_METHOD(Server::rpc_test), this);
        rb.MethodDesc("Magic test method");
        rb.ParamDesc("value", "the value");
        rb.ParamDesc("error", "error code to set");
        rb.ParamDesc("extra", "if not 0, add an extra return value");
        rb.ReturnDesc("value", "the value");
        //---------------------------------------------------------------------
    }

    void rpc_inc(FRT_RPCRequest *req)
    {
        req->GetReturn()->AddInt32(req->GetParams()->GetValue(0)._intval32 + 1);
    }

    void rpc_blob(FRT_RPCRequest *req)
    {
        req->GetReturn()->AddData(req->GetParams()->GetValue(0)._data._buf,
                                  req->GetParams()->GetValue(0)._data._len);
    }

    void rpc_test(FRT_RPCRequest *req)
    {
	int value = req->GetParams()->GetValue(0)._intval32;
	int error = req->GetParams()->GetValue(1)._intval32;
	int extra = req->GetParams()->GetValue(2)._intval8;

	req->GetReturn()->AddInt32(value);
	if (extra != 0) {
	    req->GetReturn()->AddInt32(value);
	}
	if (error != 0) {
	    req->SetError(error, "Custom error");
	}
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
    Server server(&frt.supervisor());
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
