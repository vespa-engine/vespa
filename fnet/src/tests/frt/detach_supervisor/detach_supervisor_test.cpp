// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>

#include <cassert>
#include <thread>

using namespace vespalib;
using vespalib::make_string_short::fmt;
using vespalib::test::Nexus;

CryptoEngine::SP null_crypto = std::make_shared<NullCryptoEngine>();

struct BasicFixture {
    FNET_Transport transport;
    BasicFixture() : transport(fnet::TransportConfig(4).crypto(null_crypto)) {}
    void start() { ASSERT_TRUE(transport.Start()); }
    ~BasicFixture() { transport.ShutDown(true); }
};

struct RpcFixture : FRT_Invokable {
    FRT_Supervisor                orb;
    std::atomic<FNET_Connection*> back_conn;
    RpcFixture(BasicFixture& basic) : orb(&basic.transport), back_conn(nullptr) { init_rpc(); }
    void listen() { ASSERT_TRUE(orb.Listen(0)); }
    ~RpcFixture() override;
    uint32_t                          port() const { return orb.GetListenPort(); }
    vespalib::ref_counted<FRT_Target> connect(uint32_t port) {
        return vespalib::ref_counted<FRT_Target>::internal_attach(orb.GetTarget(port));
    }
    void init_rpc() {
        FRT_ReflectionBuilder rb(&orb);
        rb.DefineMethod("inc", "l", "l", FRT_METHOD(RpcFixture::rpc_inc), this);
        rb.MethodDesc("increment a 64-bit integer");
        rb.ParamDesc("in", "an integer (64 bit)");
        rb.ReturnDesc("out", "in + 1 (64 bit)");
        rb.DefineMethod("connect", "", "", FRT_METHOD(RpcFixture::rpc_connect), this);
        rb.MethodDesc("capture 2way connection");
    }
    void rpc_inc(FRT_RPCRequest* req) {
        FRT_Values& params = *req->GetParams();
        FRT_Values& ret = *req->GetReturn();
        ret.AddInt64(params[0]._intval64 + 1);
    }
    void rpc_connect(FRT_RPCRequest* req) {
        ASSERT_TRUE(back_conn.load() == nullptr);
        back_conn.store(req->GetConnection());
        ASSERT_TRUE(back_conn.load() != nullptr);
        back_conn.load()->internal_addref();
    }
    void meta_connect(uint32_t port, vespalib::ref_counted<FRT_Target>& target) {
        target = vespalib::ref_counted<FRT_Target>::internal_attach(
            orb.Get2WayTarget(fmt("tcp/localhost:%u", port).c_str()));
        auto req = vespalib::ref_counted<FRT_RPCRequest>::internal_attach(orb.AllocRPCRequest());
        req->SetMethodName("connect");
        target->InvokeSync(req.get(), 300.0);
        ASSERT_TRUE(req->CheckReturnTypes(""));
    };
    static int check_result(FRT_RPCRequest* req, uint64_t expect) {
        int num_ok = 0;
        if (!req->CheckReturnTypes("l")) {
            EXPECT_EQ(req->GetErrorCode(), FRTE_RPC_CONNECTION);
            assert(req->GetErrorCode() == FRTE_RPC_CONNECTION);
        } else {
            uint64_t ret = req->GetReturn()->GetValue(0)._intval64;
            EXPECT_EQ(ret, expect);
            assert(ret == expect);
            ++num_ok;
        }
        return num_ok;
    }
    static int verify_rpc(FNET_Connection* conn) {
        auto req = vespalib::ref_counted<FRT_RPCRequest>::internal_attach(FRT_Supervisor::AllocRPCRequest());
        req->SetMethodName("inc");
        req->GetParams()->AddInt64(7);
        FRT_Supervisor::InvokeSync(conn->Owner()->GetScheduler(), conn, req.get(), 300.0);
        return check_result(req.get(), 8);
    }
    static int verify_rpc(FRT_Target* target) {
        auto req = vespalib::ref_counted<FRT_RPCRequest>::internal_attach(FRT_Supervisor::AllocRPCRequest());
        req->SetMethodName("inc");
        req->GetParams()->AddInt64(4);
        target->InvokeSync(req.get(), 300.0);
        return check_result(req.get(), 5);
    }
    int verify_rpc(FRT_Target* target, uint32_t port) {
        auto my_target = connect(port);
        int  num_ok = verify_rpc(target) + verify_rpc(my_target.get()) + verify_rpc(back_conn.load());
        return num_ok;
    }
};

RpcFixture::~RpcFixture() {
    if (back_conn.load() != nullptr) {
        back_conn.load()->internal_subref();
    }
}

// test timeline:
//
// listen and export server ports
// --- #1 ---
// connect to target peer
// --- #2 ---
// verify that rpc works (persistent, transient, 2way)
// --- #3 ---
// detach supervisor while talking to it
// --- #4 ---
// verify that non-detached supervisor still works
// --- #5 ---
// test cleanup

TEST(DetachSupervisorTest, require_that_supervisor_can_be_detached_from_transport) {
    constexpr size_t num_threads = 4;
    BasicFixture     f1;
    ASSERT_NO_FATAL_FAILURE(f1.start());
    uint32_t f2(0);
    uint32_t f3(0);
    uint32_t f4(0);
    uint32_t f5(0);
    auto     task = [&f1, &f2, &f3, &f4, &f5](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) { // server 1 (talks to client 1)
            auto self = std::make_unique<RpcFixture>(f1);
            ASSERT_NO_FATAL_FAILURE(self->listen());
            f2 = self->port();
            ctx.barrier(); // #1
            vespalib::ref_counted<FRT_Target> target;
            ASSERT_NO_FATAL_FAILURE(self->meta_connect(f4, target));
            auto client_target = self->connect(f3);
            ctx.barrier(); // #2
            ctx.barrier(); // #3
            std::this_thread::sleep_for(50ms);
            self.reset();                                              // <--- detach supervisor for server 1
            ctx.barrier();                                             // #4
            EXPECT_EQ(RpcFixture::verify_rpc(target.get()), 0);        // outgoing 2way target should be closed
            EXPECT_EQ(RpcFixture::verify_rpc(client_target.get()), 1); // pure client target should not be closed
            ctx.barrier();                                             // #5
        } else if (thread_id == 1) {                                   // server 2 (talks to client 2)
            auto self = std::make_unique<RpcFixture>(f1);
            ASSERT_NO_FATAL_FAILURE(self->listen());
            f3 = self->port();
            ctx.barrier(); // #1
            vespalib::ref_counted<FRT_Target> target;
            ASSERT_NO_FATAL_FAILURE(self->meta_connect(f5, target));
            ctx.barrier();           // #2
            ctx.barrier();           // #3
            ctx.barrier();           // #4
            ctx.barrier();           // #5
        } else if (thread_id == 2) { // client 1 (talks to server 1)
            auto self = std::make_unique<RpcFixture>(f1);
            ASSERT_NO_FATAL_FAILURE(self->listen());
            f4 = self->port();
            ctx.barrier(); // #1
            auto target = self->connect(f2);
            ctx.barrier(); // #2
            ASSERT_TRUE(self->back_conn.load() != nullptr);
            EXPECT_EQ(self->verify_rpc(target.get(), f2), 3);
            ctx.barrier(); // #3
            auto until = steady_clock::now() + 120s;
            while ((self->verify_rpc(target.get(), f2) > 0) && (steady_clock::now() < until)) {
                // wait until peer is fully detached
            }
            ctx.barrier(); // #4
            EXPECT_EQ(self->verify_rpc(target.get(), f2), 0);
            ctx.barrier(); // #5
        } else {           // client 2 (talks to server 2)
            ASSERT_EQ(thread_id, 3u);
            auto self = std::make_unique<RpcFixture>(f1);
            ASSERT_NO_FATAL_FAILURE(self->listen());
            f5 = self->port();
            ctx.barrier(); // #1
            auto target = self->connect(f3);
            ctx.barrier(); // #2
            ASSERT_TRUE(self->back_conn.load() != nullptr);
            EXPECT_EQ(self->verify_rpc(target.get(), f3), 3);
            ctx.barrier(); // #3
            ctx.barrier(); // #4
            EXPECT_EQ(self->verify_rpc(target.get(), f3), 3);
            ctx.barrier(); // #5
        }
    };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
