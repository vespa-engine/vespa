// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <thread>

struct Receptor : public FRT_IRequestWait
{
    FRT_RPCRequest *req;

    Receptor() : req(0) {}
    void RequestDone(FRT_RPCRequest *r) override {
        req = r;
    }
};

struct Server : public FRT_Invokable
{
    FRT_Supervisor &orb;
    Receptor       &receptor;

    Server(FRT_Supervisor &s, Receptor &r) : orb(s), receptor(r) {
        FRT_ReflectionBuilder rb(&s);
        rb.DefineMethod("hook", "", "",
                        FRT_METHOD(Server::rpc_hook), this);
    }

    void rpc_hook(FRT_RPCRequest *req) {
        FNET_Connection *conn = req->GetConnection();
        conn->AddRef(); // need to keep it alive
        req->Detach();
        req->Return();  // will free request channel
        FRT_RPCRequest *r = orb.AllocRPCRequest();
        r->SetMethodName("frt.rpc.ping");
        // might re-use request channel before it is unlinked from hashmap
        orb.InvokeAsync(orb.GetTransport(), conn, r, 5.0, &receptor);
        conn->SubRef(); // invocation will now keep the connection alive as needed
    }
};

TEST("detach return invoke") {
    Receptor receptor;
    fnet::frt::StandaloneFRT frtServer;
    FRT_Supervisor & supervisor = frtServer.supervisor();
    Server server(supervisor, receptor);
    ASSERT_TRUE(supervisor.Listen(0));
    std::string spec =  vespalib::make_string("tcp/localhost:%d", supervisor.GetListenPort());
    FRT_Target *target = supervisor.Get2WayTarget(spec.c_str());
    FRT_RPCRequest *req = supervisor.AllocRPCRequest();

    req->SetMethodName("hook");
    target->InvokeSync(req, 5.0);
    EXPECT_TRUE(!req->IsError());
    for (uint32_t i = 0; i < 1000; ++i) {
        if (receptor.req != 0) {
            break;
        }
        std::this_thread::sleep_for(10ms);
    }
    req->SubRef();
    target->SubRef();
    if (receptor.req != 0) {
        EXPECT_TRUE(!receptor.req->IsError());
        receptor.req->SubRef();
    }
    EXPECT_TRUE(receptor.req != 0);
};

TEST_MAIN() { TEST_RUN_ALL(); }
