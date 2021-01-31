// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/slobrok/server/slobrokserver.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <thread>

//-----------------------------------------------------------------------------

class Server : public FRT_Invokable
{
private:
    fnet::frt::StandaloneFRT _server;
    std::string    _name;

public:
    Server(std::string name, int port);
    ~Server();
    void rpc_listNamesServed(FRT_RPCRequest *req);
};


Server::Server(std::string name, int port)
    : _server(),
      _name(name)
{
    {
        FRT_ReflectionBuilder rb(&_server.supervisor());
        //---------------------------------------------------------------------
        rb.DefineMethod("slobrok.callback.listNamesServed", "", "S",
                        FRT_METHOD(Server::rpc_listNamesServed), this);
        rb.MethodDesc("Look up a rpcserver");
        rb.ReturnDesc("names", "The rpcserver names on this server");
        //---------------------------------------------------------------------
    }
    _server.supervisor().Listen(port);
}


void
Server::rpc_listNamesServed(FRT_RPCRequest *req)
{
    FRT_Values &dst = *req->GetReturn();
    FRT_StringValue *names = dst.AddStringArray(1);
    dst.SetString(&names[0], _name.c_str());
}


Server::~Server() = default;

namespace {

bool checkOk(FRT_RPCRequest *req)
{
    if (req == nullptr) {
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

template<typename T>
class SubReferer
{
private:
    T* &_t;
public:
    SubReferer(T* &t) : _t(t) {}
    ~SubReferer() {
        if (_t != nullptr) _t->SubRef();
    }
};


template<typename T>
class ShutDowner
{
private:
    T &_t;
public:
    ShutDowner(T &t) : _t(t) {}
    ~ShutDowner() {
        _t.ShutDown(true);
    }
};


template<typename T>
class Stopper
{
private:
    T &_t;
public:
    Stopper(T &t) : _t(t) {}
    ~Stopper() {
        _t.stop();
    }
};

} // namespace <unnamed>

//-----------------------------------------------------------------------------

TEST("standalone") {
    slobrok::SlobrokServer slobrokServer(18541);
    Stopper<slobrok::SlobrokServer> ssCleaner(slobrokServer);

    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & orb = server.supervisor();

    FRT_Target     *sb  = orb.GetTarget(18541);
    SubReferer<FRT_Target> sbCleaner(sb);

    FRT_RPCRequest *req = nullptr;
    SubReferer<FRT_RPCRequest> reqCleaner(req);

    for (int retry=0; retry < 5*61; retry++) {
        // test ping against slobrok
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("frt.rpc.ping");
        sb->InvokeSync(req, 5.0);
        if (checkOk(req)) {
            break;
        }
        fprintf(stderr, "ping failed [retry %d]\n", retry);
        std::this_thread::sleep_for(200ms);
        sb->SubRef();
        sb = orb.GetTarget(18541);
    }
    ASSERT_TRUE(checkOk(req));

    // lookup '*' on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // check managed servers on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.internal.listManagedRpcServers");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    Server a("A", 18542);

    // register server A
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.registerRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18542");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));

    // lookup '*' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18542") == 0);

    // lookup 'A' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18542") == 0);

    // lookup 'B' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("B");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // lookup '*/*' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*/*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    {
        Server b("B", 18543);

        // register server B as 'C'
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("C");
        req->GetParams()->AddString("tcp/localhost:18543");
        sb->InvokeSync(req, 5.0);
        ASSERT_TRUE(req->IsError());

        // register server B
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.registerRpcServer");
        req->GetParams()->AddString("B");
        req->GetParams()->AddString("tcp/localhost:18543");
        sb->InvokeSync(req, 5.0);
        ASSERT_TRUE(checkOk(req));

        {
            Server a2("A", 18544);

            // register server A(2)
            req = orb.AllocRPCRequest(req);
            req->SetMethodName("slobrok.registerRpcServer");
            req->GetParams()->AddString("A");
            req->GetParams()->AddString("tcp/localhost:18544");
            sb->InvokeSync(req, 5.0);
            ASSERT_TRUE(req->IsError());
        }

        // lookup '*' should give 'AB | BA'
        req = orb.AllocRPCRequest(req);
        req->SetMethodName("slobrok.lookupRpcServer");
        req->GetParams()->AddString("*");
        sb->InvokeSync(req, 5.0);
        ASSERT_TRUE(checkOk(req));
        ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
        ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 2);
        ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 2);
        {
            FRT_StringValue *name = req->GetReturn()->GetValue(0)._string_array._pt;
            FRT_StringValue *spec = req->GetReturn()->GetValue(1)._string_array._pt;
            if (strcmp(name[0]._str, "A") == 0) {
                ASSERT_TRUE(strcmp(name[0]._str, "A") == 0);
                ASSERT_TRUE(strcmp(name[1]._str, "B") == 0);
                ASSERT_TRUE(strcmp(spec[0]._str, "tcp/localhost:18542") == 0);
                ASSERT_TRUE(strcmp(spec[1]._str, "tcp/localhost:18543") == 0);
            } else {
                ASSERT_TRUE(strcmp(name[1]._str, "A") == 0);
                ASSERT_TRUE(strcmp(name[0]._str, "B") == 0);
                ASSERT_TRUE(strcmp(spec[1]._str, "tcp/localhost:18542") == 0);
                ASSERT_TRUE(strcmp(spec[0]._str, "tcp/localhost:18543") == 0);
            }
        }
    }

    std::this_thread::sleep_for(2s);

    // lookup 'B' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("B");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // unregister server A (wrong spec)
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18543");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(req->IsError());

    // lookup 'A' should give 'A'
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 1);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 1);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(0)._string_array._pt[0]._str, "A") == 0);
    ASSERT_TRUE(strcmp(req->GetReturn()->GetValue(1)._string_array._pt[0]._str, "tcp/localhost:18542") == 0);

    // unregister server A
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18542");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));

    // lookup 'A' should give ''
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("A");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // lookup '*' on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.lookupRpcServer");
    req->GetParams()->AddString("*");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
    ASSERT_TRUE(strcmp(req->GetReturnSpec(), "SS") == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(0)._string_array._len == 0);
    ASSERT_TRUE(req->GetReturn()->GetValue(1)._string_array._len == 0);

    // unregister server A on empty slobrok
    req = orb.AllocRPCRequest(req);
    req->SetMethodName("slobrok.unregisterRpcServer");
    req->GetParams()->AddString("A");
    req->GetParams()->AddString("tcp/localhost:18542");
    sb->InvokeSync(req, 5.0);
    ASSERT_TRUE(checkOk(req));
}

TEST_MAIN() { TEST_RUN_ALL(); }
