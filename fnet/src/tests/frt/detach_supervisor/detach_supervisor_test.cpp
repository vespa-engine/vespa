// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/transport.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <thread>

using namespace vespalib;
using vespalib::make_string_short::fmt;

CryptoEngine::SP null_crypto = std::make_shared<NullCryptoEngine>();

struct BasicFixture {
    FNET_Transport    transport;
    BasicFixture() : transport(fnet::TransportConfig(4).crypto(null_crypto)) {
        ASSERT_TRUE(transport.Start());
    }
    ~BasicFixture() {
        transport.ShutDown(true);
    }
};

struct RpcFixture : FRT_Invokable {
    FRT_Supervisor orb;
    std::atomic<FNET_Connection *> back_conn;
    RpcFixture(BasicFixture &basic) : orb(&basic.transport), back_conn(nullptr) {
        init_rpc();
        ASSERT_TRUE(orb.Listen(0));
    }
    ~RpcFixture() {
        if (back_conn.load() != nullptr) {
            back_conn.load()->SubRef();
        }
    }
    uint32_t port() const { return orb.GetListenPort(); }
    FRT_Target *connect(uint32_t port) {
        return orb.GetTarget(port);
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
    void rpc_inc(FRT_RPCRequest *req) {
        FRT_Values &params = *req->GetParams();
        FRT_Values &ret    = *req->GetReturn();
        ret.AddInt64(params[0]._intval64 + 1);
    }
    void rpc_connect(FRT_RPCRequest *req) {
        ASSERT_TRUE(back_conn.load() == nullptr);
        back_conn.store(req->GetConnection());
        ASSERT_TRUE(back_conn.load() != nullptr);
        back_conn.load()->AddRef();
    }
    FRT_Target *meta_connect(uint32_t port) {
        auto *target = orb.Get2WayTarget(fmt("tcp/localhost:%u", port).c_str());
        auto *req = orb.AllocRPCRequest();
        req->SetMethodName("connect");
        target->InvokeSync(req, 300.0);
        ASSERT_TRUE(req->CheckReturnTypes(""));
        req->SubRef();
        return target;
    };
    static int check_result(FRT_RPCRequest *req, uint64_t expect) {
        int num_ok = 0;
        if (!req->CheckReturnTypes("l")) {
            ASSERT_EQUAL(req->GetErrorCode(), FRTE_RPC_CONNECTION);
        } else {
            uint64_t ret = req->GetReturn()->GetValue(0)._intval64;
            ASSERT_EQUAL(ret, expect);
            ++num_ok;
        }
        req->SubRef();
        return num_ok;
    }
    static int verify_rpc(FNET_Connection *conn) {
        auto *req = FRT_Supervisor::AllocRPCRequest();
        req->SetMethodName("inc");
        req->GetParams()->AddInt64(7);
        FRT_Supervisor::InvokeSync(conn->Owner()->GetScheduler(), conn, req, 300.0);
        return check_result(req, 8);
    }
    static int verify_rpc(FRT_Target *target) {
        auto *req = FRT_Supervisor::AllocRPCRequest();
        req->SetMethodName("inc");
        req->GetParams()->AddInt64(4);
        target->InvokeSync(req, 300.0);
        return check_result(req, 5);
    }
    int verify_rpc(FRT_Target *target, uint32_t port) {
        auto *my_target = connect(port);
        int num_ok = verify_rpc(target) + verify_rpc(my_target) + verify_rpc(back_conn.load());
        my_target->SubRef();
        return num_ok;
    }
};

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

TEST_MT_FFFFF("require that supervisor can be detached from transport", 4, BasicFixture(), uint32_t(), uint32_t(), uint32_t(), uint32_t()) {
    if (thread_id == 0) {        // server 1 (talks to client 1)
        auto self = std::make_unique<RpcFixture>(f1);
        f2 = self->port();
        TEST_BARRIER(); // #1
        auto *target = self->meta_connect(f4);
        auto *client_target = self->connect(f3);
        TEST_BARRIER(); // #2
        TEST_BARRIER(); // #3
        std::this_thread::sleep_for(50ms);
        self.reset();   // <--- detach supervisor for server 1
        TEST_BARRIER(); // #4
        EXPECT_EQUAL(RpcFixture::verify_rpc(target), 0); // outgoing 2way target should be closed
        EXPECT_EQUAL(RpcFixture::verify_rpc(client_target), 1); // pure client target should not be closed
        TEST_BARRIER(); // #5
        target->SubRef();
        client_target->SubRef();
    } else if (thread_id == 1) { // server 2 (talks to client 2)
        auto self = std::make_unique<RpcFixture>(f1);
        f3 = self->port();
        TEST_BARRIER(); // #1
        auto *target = self->meta_connect(f5);
        TEST_BARRIER(); // #2
        TEST_BARRIER(); // #3
        TEST_BARRIER(); // #4
        TEST_BARRIER(); // #5
        target->SubRef();
    } else if (thread_id == 2) { // client 1 (talks to server 1)
        auto self = std::make_unique<RpcFixture>(f1);
        f4 = self->port();
        TEST_BARRIER(); // #1
        auto *target = self->connect(f2);
        TEST_BARRIER(); // #2
        ASSERT_TRUE(self->back_conn.load() != nullptr);
        EXPECT_EQUAL(self->verify_rpc(target, f2), 3);
        TEST_BARRIER(); // #3
        auto until = steady_clock::now() + 120s;
        while ((self->verify_rpc(target, f2) > 0) &&
               (steady_clock::now() < until))
        {
            // wait until peer is fully detached
        }
        TEST_BARRIER(); // #4
        EXPECT_EQUAL(self->verify_rpc(target, f2), 0);
        TEST_BARRIER(); // #5
        target->SubRef();
    } else {                     // client 2 (talks to server 2)
        ASSERT_EQUAL(thread_id, 3u);
        auto self = std::make_unique<RpcFixture>(f1);
        f5 = self->port();
        TEST_BARRIER(); // #1
        auto *target = self->connect(f3);
        TEST_BARRIER(); // #2
        ASSERT_TRUE(self->back_conn.load() != nullptr);
        EXPECT_EQUAL(self->verify_rpc(target, f3), 3);
        TEST_BARRIER(); // #3
        TEST_BARRIER(); // #4
        EXPECT_EQUAL(self->verify_rpc(target, f3), 3);
        TEST_BARRIER(); // #5
        target->SubRef();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
