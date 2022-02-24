// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/fnet/transport_debugger.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/invoker.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>

vespalib::CryptoEngine::SP tls_crypto = std::make_shared<vespalib::TlsCryptoEngine>(vespalib::test::make_tls_options_for_testing());

struct Service : FRT_Invokable {
    fnet::frt::StandaloneFRT frt;
    Service(fnet::TimeTools::SP time_tools)
      : frt(fnet::TransportConfig(4).crypto(tls_crypto).time_tools(time_tools))
    {
        init_rpc();
        ASSERT_TRUE(frt.supervisor().Listen(0));
    }
    FNET_Transport &transport() { return *frt.supervisor().GetTransport(); }
    int listen_port() const {
        return frt.supervisor().GetListenPort();
    }
    FRT_Target *connect(int port) {
        return frt.supervisor().GetTarget(port);
    }
    void init_rpc() {
        FRT_ReflectionBuilder rb(&frt.supervisor());
        rb.DefineMethod("inc", "l", "l", FRT_METHOD(Service::rpc_inc), this);
        rb.MethodDesc("increment a 64-bit integer, returns after 5 seconds");
        rb.ParamDesc("in", "an integer (64 bit)");
        rb.ReturnDesc("out", "in + 1 (64 bit)");
    }
    struct ReturnLater : FNET_Task {
        FRT_RPCRequest *req;
        ReturnLater(FNET_Scheduler *scheduler, FRT_RPCRequest *req_in)
          : FNET_Task(scheduler), req(req_in) {}
        void PerformTask() override { req->Return(); }
    };
    void rpc_inc(FRT_RPCRequest *req) {
        req->Detach();
        FRT_Values &params = *req->GetParams();
        FRT_Values &ret    = *req->GetReturn();
        ret.AddInt64(params[0]._intval64 + 1);
        auto my_scheduler = req->GetConnection()->Owner()->GetScheduler();
        auto &task = req->getStash().create<ReturnLater>(my_scheduler, req);
        task.Schedule(5.0);
    }
    ~Service() = default;
};

struct Fixture {
    fnet::TransportDebugger debugger;
    Service server;
    Service client;
    Fixture()
      : debugger(),
        server(debugger.time_tools()),
        client(debugger.time_tools())
    {
        debugger.attach({server.transport(), client.transport()});
    }
    ~Fixture() {
        debugger.detach();
    }
};

struct MyWait : FRT_IRequestWait {
    FRT_RPCRequest *req = nullptr;
    void RequestDone(FRT_RPCRequest *r) override { req = r; }
};

TEST_FF("transport layers can be run with transport debugger", Fixture(), vespalib::TimeBomb(60)) {
    MyWait w4; // short timeout, should fail
    MyWait w6; // long timeout, should be ok

    FRT_Target *target = f1.client.connect(f1.server.listen_port());

    FRT_RPCRequest *req4 = f1.client.frt.supervisor().AllocRPCRequest();
    req4->SetMethodName("inc");
    req4->GetParams()->AddInt64(3);
    target->InvokeAsync(req4, 4.0, &w4);

    FRT_RPCRequest *req6 = f1.client.frt.supervisor().AllocRPCRequest();
    req6->SetMethodName("inc");
    req6->GetParams()->AddInt64(7);
    target->InvokeAsync(req6, 6.0, &w6);

    bool got4 = false;
    bool got6 = false;
    size_t steps = 0;

    while (!(got4 && got6)) {
        f1.debugger.step();
        ++steps;
        if (!got4 && w4.req) {
            got4 = true;
            fprintf(stderr, "request with 4s timeout completed after %zu steps (~%zu ms)\n", steps, steps * 5);
        }
        if (!got6 && w6.req) {
            got6 = true;
            fprintf(stderr, "request with 6s timeout completed after %zu steps (~%zu ms)\n", steps, steps * 5);
        }
    }
    ASSERT_EQUAL(req4, w4.req);
    ASSERT_EQUAL(req6, w6.req);
    EXPECT_EQUAL(req4->GetErrorCode(), FRTE_RPC_TIMEOUT);
    ASSERT_TRUE(req6->CheckReturnTypes("l"));
    EXPECT_EQUAL(req6->GetReturn()->GetValue(0)._intval64, 8u);
    target->SubRef();
    req4->SubRef();
    req6->SubRef();
}

TEST_MAIN() { TEST_RUN_ALL(); }
