// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <atomic>
#include <thread>

struct Receptor : public FRT_IRequestWait {
    std::atomic<FRT_RPCRequest*> req;

    Receptor() : req(nullptr) {}
    void RequestDone(FRT_RPCRequest* r) override { req.store(r); }
};

struct Server : public FRT_Invokable {
    FRT_Supervisor& orb;
    Receptor&       receptor;

    Server(FRT_Supervisor& s, Receptor& r) : orb(s), receptor(r) {
        FRT_ReflectionBuilder rb(&s);
        rb.DefineMethod("hook", "", "", FRT_METHOD(Server::rpc_hook), this);
    }

    void rpc_hook(FRT_RPCRequest* req) {
        FNET_Connection* conn = req->GetConnection();
        conn->internal_addref(); // need to keep it alive
        req->Detach();
        req->Return(); // will free request channel
        FRT_RPCRequest* r = orb.AllocRPCRequest();
        r->SetMethodName("frt.rpc.ping");
        // might re-use request channel before it is unlinked from hashmap
        orb.InvokeAsync(orb.GetTransport(), conn, r, 5.0, &receptor);
        conn->internal_subref(); // invocation will now keep the connection alive as needed
    }
};

TEST(DetachReturnInvokeTest, detach_return_invoke) {
    Receptor                 receptor;
    fnet::frt::StandaloneFRT frtServer;
    FRT_Supervisor&          supervisor = frtServer.supervisor();
    Server                   server(supervisor, receptor);
    ASSERT_TRUE(supervisor.Listen(0));
    std::string     spec = vespalib::make_string("tcp/localhost:%d", supervisor.GetListenPort());
    FRT_Target*     target = supervisor.Get2WayTarget(spec.c_str());
    FRT_RPCRequest* req = supervisor.AllocRPCRequest();

    req->SetMethodName("hook");
    target->InvokeSync(req, 5.0);
    EXPECT_TRUE(!req->IsError());
    for (uint32_t i = 0; i < 1000; ++i) {
        if (receptor.req.load() != nullptr) {
            break;
        }
        std::this_thread::sleep_for(10ms);
    }
    req->internal_subref();
    target->internal_subref();
    if (receptor.req.load() != nullptr) {
        EXPECT_TRUE(!receptor.req.load()->IsError());
        receptor.req.load()->internal_subref();
    }
    EXPECT_TRUE(receptor.req.load() != nullptr);
};

GTEST_MAIN_RUN_ALL_TESTS()
