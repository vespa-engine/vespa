// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/frt.h>
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

//-----------------------------------------------------------------------------

TEST("multi") {
    FRT_Supervisor orb;
    orb.Start();

    FRT_Target     *sb  = orb.GetTarget(18511);
    FRT_RPCRequest *req = NULL;

    // test ping against slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("frt.rpc.ping");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());

    // lookup '*' on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // check managed servers on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.internal.listManagedRpcServers");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    Server a("A", 18518);

    // register server A
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.registerRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18518");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());

    // lookup '*' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18518") == 0);

    // lookup 'A' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18518") == 0);

    // lookup 'B' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("B");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // lookup '*/*' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*/*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    {
        Server b("B", 18519);

        // register server B as 'C'
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("C");
        req->GetParams()->AddString("tcp/localhost:18519");
        sb->InvokeSync(req, 5.0);
        ASSERT_TRUE(req->IsError());

        // register server B
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("B");
        req->GetParams()->AddString("tcp/localhost:18519");
        sb->InvokeSync(req, 5.0);
        ASSERT_TRUE(!req->IsError());

        {
            Server a2("A", 18520);

            // register server A(2)
            req = orb.AllocRPCRequest(req);
            req->SetMethodName("slobrok.registerRpcServer");
            req->GetParams()->AddString("A");
            req->GetParams()->AddString("tcp/localhost:18520");
            sb->InvokeSync(req, 5.0);
            ASSERT_TRUE(req->IsError());
        }

        // lookup '*' should give 'AB | BA'
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.lookupRpcServer");
        req->GetParams()->AddString("*");
        sb->InvokeSync(req, 5.0);
        ASSERT_TRUE(!req->IsError());
        ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
        ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 2);
        ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 2);
        {
            FRT_StringValue *name = req->GetReturn()->GetValue(0)._string_array._pt;
            FRT_StringValue *spec = req->GetReturn()->GetValue(1)._string_array._pt;
            if (strcmp(name[0]._str, "A") == 0) {
                ASSERT_TRUE(strcmp(name[0]._str, "A") == 0);
                ASSERT_TRUE(strcmp(name[1]._str, "B") == 0);
                ASSERT_TRUE(strcmp(spec[0]._str, "tcp/localhost:18518") == 0);
                ASSERT_TRUE(strcmp(spec[1]._str, "tcp/localhost:18519") == 0);
            } else {
                ASSERT_TRUE(strcmp(name[1]._str, "A") == 0);
                ASSERT_TRUE(strcmp(name[0]._str, "B") == 0);
                ASSERT_TRUE(strcmp(spec[1]._str, "tcp/localhost:18518") == 0);
                ASSERT_TRUE(strcmp(spec[0]._str, "tcp/localhost:18519") == 0);
            }
        }
    }

    FastOS_Thread::Sleep(2000);

    // lookup 'B' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("B");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // unregister server A (wrong spec)
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18519");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(req->IsError());

    // lookup 'A' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18518") == 0);

    // unregister server A
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18518");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());

    // lookup 'A' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // lookup '*' on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // unregister server A on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18518");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());


    FRT_Target     *sb1  = orb.GetTarget(18512);
    FRT_Target     *sb2  = orb.GetTarget(18513);
    FRT_Target     *sb3  = orb.GetTarget(18514);
    FRT_Target     *sb4  = orb.GetTarget(18515);
    FRT_Target     *sb5  = orb.GetTarget(18516);
    FRT_Target     *sb6  = orb.GetTarget(18517);

    // register server A
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.registerRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18518");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError());


    Server c("C", 18521);
    Server d("D", 18522);

    for (int i=0; i < 150; i++) {
        // register server C
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("C");
        req->GetParams()->AddString("tcp/localhost:18521");
        sb1->InvokeSync(req, 5.0);
        ASSERT_TRUE(!req->IsError());

        // register server D
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("D");
        req->GetParams()->AddString("tcp/localhost:18522");
        sb2->InvokeSync(req, 5.0);
        ASSERT_TRUE(!req->IsError());

	// lookup 'C' should give 'C'
	req = orb.AllocRPCRequest(req);
	req->SetMethodName("slobrok.lookupRpcServer");
	req->GetParams()->AddString("C");
	sb3->InvokeSync(req, 5.0);
	ASSERT_TRUE(!req->IsError());
	ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
	ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
	ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "C") == 0);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18521") == 0);

	// lookup 'C' should give 'C'
	req = orb.AllocRPCRequest(req);
	req->SetMethodName("slobrok.lookupRpcServer");
	req->GetParams()->AddString("C");
	sb4->InvokeSync(req, 5.0);
	ASSERT_TRUE(!req->IsError());
	ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
	ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
	ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "C") == 0);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18521") == 0);

	// lookup 'C' should give 'C'
	req = orb.AllocRPCRequest(req);
	req->SetMethodName("slobrok.lookupRpcServer");
	req->GetParams()->AddString("C");
	sb5->InvokeSync(req, 5.0);
	ASSERT_TRUE(!req->IsError());
	ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
	ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
	ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "C") == 0);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18521") == 0);

	// lookup 'C' should give 'C'
	req = orb.AllocRPCRequest(req);
	req->SetMethodName("slobrok.lookupRpcServer");
	req->GetParams()->AddString("C");
	sb6->InvokeSync(req, 5.0);
	ASSERT_TRUE(!req->IsError());
	ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
	ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
	ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "C") == 0);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18521") == 0);

        FastOS_Thread::Sleep(200);

	// lookup 'D' should give 'D'
	req = orb.AllocRPCRequest(req);
	req->SetMethodName("slobrok.lookupRpcServer");
	req->GetParams()->AddString("D");
	sb->InvokeSync(req, 5.0);
	ASSERT_TRUE(!req->IsError());
	ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
	ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
	ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "D") == 0);
	ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18522") == 0);
    }

    orb.ShutDown(true);
}

TEST_MAIN() { TEST_RUN_ALL(); }
