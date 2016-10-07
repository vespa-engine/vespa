// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/frt.h>
#include <vespa/slobrok/server/slobrokserver.h>
#include <string>


//-----------------------------------------------------------------------------

class Server : public FRT_Invokable
{
private:
    FRT_Supervisor _orb;
    std::string    _name;

public:
    Server(std::string name, int port);
    ~Server();
    void rpc_listNamesServed(FRT_RPCRequest *req);
};


Server::Server(std::string name, int port)
    : _orb(),
      _name(name)
{
    {
        FRT_ReflectionBuilder rb(&_orb);
        //---------------------------------------------------------------------
        rb.DefineMethod("slobrok.callback.listNamesServed", "", "S", true,
                        FRT_METHOD(Server::rpc_listNamesServed), this);
        rb.MethodDesc("Look up a rpcserver");
        rb.ReturnDesc("names", "The rpcserver names on this server");
        //---------------------------------------------------------------------
    }
    _orb.Listen(port);
    _orb.Start();
}


void
Server::rpc_listNamesServed(FRT_RPCRequest *req)
{
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(1);
    dst.SetString(&names[0], _name.c_str());
}


Server::~Server()
{
    _orb.ShutDown(true);
}

namespace {

bool checkOk(FRT_RPCRequest *req)
{
    if (req == NULL) {
        fprintf(stderr, "req is null pointer, this is bad\n");
        return false;
    }
    if (req->IsError()) {
        fprintf(stderr, "req FAILED [code %d]: %s\n",
                req->GetErrorCode(),
                req->GetErrorMessage());
        fprintf(stderr, "req method is: '%s' with params:\n", req->GetMethodName());
        req->GetParams()->Print();
        fflush(stdout); // flushes output from Print() on previous line
        return false;
    } else {
        return true;
    }
}

} // namespace <unnamed>

//-----------------------------------------------------------------------------

TEST("standalone") {
    slobrok::SlobrokServer slobrokServer(18541);
    FastOS_Thread::Sleep(300);

    FRT_Supervisor orb;
    orb.Start();

    FRT_Target     *sb  = orb.GetTarget(18541);
    FRT_RPCRequest *req = NULL;

    // test ping against slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("frt.rpc.ping");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));

    // lookup '*' on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // check managed servers on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.internal.listManagedRpcServers");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    Server a("A", 18542);

    // register server A
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.registerRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18542");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));

    // lookup '*' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    EXPECT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    EXPECT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18542") == 0);

    // lookup 'A' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    EXPECT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    EXPECT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18542") == 0);

    // lookup 'B' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("B");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // lookup '*/*' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*/*");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    {
        Server b("B", 18543);

        // register server B as 'C'
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("C");
        req->GetParams()->AddString("tcp/localhost:18543");
        sb->InvokeSync(req, 5.0);
        EXPECT_TRUE(!checkOk(req));

        // register server B
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("B");
        req->GetParams()->AddString("tcp/localhost:18543");
        sb->InvokeSync(req, 5.0);
        EXPECT_TRUE(checkOk(req));

        {
            Server a2("A", 18544);

            // register server A(2)
            req = orb.AllocRPCRequest(req);
            req->SetMethodName("slobrok.registerRpcServer");
            req->GetParams()->AddString("A");
            req->GetParams()->AddString("tcp/localhost:18544");
            sb->InvokeSync(req, 5.0);
            EXPECT_TRUE(!checkOk(req));
        }

        // lookup '*' should give 'AB | BA'
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.lookupRpcServer");
        req->GetParams()->AddString("*");
        sb->InvokeSync(req, 5.0);
        EXPECT_TRUE(checkOk(req));
        EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
        EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 2);
        EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 2);
        {
            FRT_StringValue *name = req->GetReturn()->GetValue(0)._string_array._pt;
            FRT_StringValue *spec = req->GetReturn()->GetValue(1)._string_array._pt;
            if (strcmp(name[0]._str, "A") == 0) {
                EXPECT_TRUE(strcmp(name[0]._str, "A") == 0);
                EXPECT_TRUE(strcmp(name[1]._str, "B") == 0);
                EXPECT_TRUE(strcmp(spec[0]._str, "tcp/localhost:18542") == 0);
                EXPECT_TRUE(strcmp(spec[1]._str, "tcp/localhost:18543") == 0);
            } else {
                EXPECT_TRUE(strcmp(name[1]._str, "A") == 0);
                EXPECT_TRUE(strcmp(name[0]._str, "B") == 0);
                EXPECT_TRUE(strcmp(spec[1]._str, "tcp/localhost:18542") == 0);
                EXPECT_TRUE(strcmp(spec[0]._str, "tcp/localhost:18543") == 0);
            }
        }
    }

    FastOS_Thread::Sleep(2000);

    // lookup 'B' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("B");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // unregister server A (wrong spec)
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18543");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(!checkOk(req));

    // lookup 'A' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    EXPECT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    EXPECT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18542") == 0);

    // unregister server A
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18542");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));

    // lookup 'A' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // lookup '*' on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));
    EXPECT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    EXPECT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // unregister server A on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18542");
    sb->InvokeSync(req, 5.0);
    EXPECT_TRUE(checkOk(req));

    sb->SubRef();
    req->SubRef();

    slobrokServer.stop();
    orb.ShutDown(true);
}

TEST_MAIN() { TEST_RUN_ALL(); }
