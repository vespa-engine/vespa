// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/size_literals.h>

#include <latch>
#include <thread>

using namespace vespalib;
using vespalib::test::Nexus;

struct Rpc : FRT_Invokable {
    FNET_Transport transport;
    FRT_Supervisor orb;
    Rpc(CryptoEngine::SP crypto, size_t num_threads, bool drop_empty)
        : transport(fnet::TransportConfig(num_threads).crypto(std::move(crypto)).drop_empty_buffers(drop_empty)),
          orb(&transport) {}
    void start() { ASSERT_TRUE(transport.Start()); }
    void listen(uint32_t& port) {
        ASSERT_TRUE(orb.Listen(0));
        port = orb.GetListenPort();
    }
    FRT_Target* connect(uint32_t port) { return orb.GetTarget(port); }
    ~Rpc() override { transport.ShutDown(true); }
};

struct Server : Rpc {
    uint32_t port;
    Server(CryptoEngine::SP crypto, size_t num_threads, bool drop_empty = false)
        : Rpc(std::move(crypto), num_threads, drop_empty), port(0) {}
    ~Server() override;
    void start() {
        ASSERT_NO_FATAL_FAILURE(listen(port));
        init_rpc();
        ASSERT_NO_FATAL_FAILURE(Rpc::start());
    }
    void init_rpc() {
        FRT_ReflectionBuilder rb(&orb);
        rb.DefineMethod("inc", "l", "l", FRT_METHOD(Server::rpc_inc), this);
        rb.MethodDesc("increment a 64-bit integer");
        rb.ParamDesc("in", "an integer (64 bit)");
        rb.ReturnDesc("out", "in + 1 (64 bit)");
    }
    void rpc_inc(FRT_RPCRequest* req) {
        FRT_Values& params = *req->GetParams();
        FRT_Values& ret = *req->GetReturn();
        ret.AddInt64(params[0]._intval64 + 1);
    }
};

Server::~Server() = default;

struct Client : Rpc {
    uint32_t port;
    Client(CryptoEngine::SP crypto, size_t num_threads, const Server& server, bool drop_empty = false)
        : Rpc(std::move(crypto), num_threads, drop_empty), port(server.port) {}
    ~Client() override;
    FRT_Target* connect() { return Rpc::connect(port); }
};

Client::~Client() = default;

struct Result {
    std::vector<double> req_per_sec;
    explicit Result(size_t num_threads) : req_per_sec(num_threads, 0.0) {}
    double throughput() const {
        double sum = 0.0;
        for (double sample : req_per_sec) {
            sum += sample;
        }
        return sum;
    }
    double latency_ms() const {
        double avg_req_per_sec = throughput() / req_per_sec.size();
        double avg_sec_per_req = 1.0 / avg_req_per_sec;
        return avg_sec_per_req * 1000.0;
    }
    void print() const {
        fprintf(stderr, "total throughput: %f req/s\n", throughput());
        fprintf(stderr, "average latency : %f ms\n", latency_ms());
    }
};

bool   verbose = false;
double budget = 1.5;

void perform_test(size_t thread_id, std::latch& latch, Client& client, Result& result, bool vital) {
    if (!vital && !verbose) {
        if (thread_id == 0) {
            fprintf(stderr, "... skipping non-vital test; run with 'verbose' to enable\n");
        }
        return;
    }
    uint64_t        seq = 0;
    FRT_Target*     target = client.connect();
    FRT_RPCRequest* req = client.orb.AllocRPCRequest();
    auto            invoke = [&seq, target, &client, &req]() {
        req = client.orb.AllocRPCRequest(req);
        req->SetMethodName("inc");
        req->GetParams()->AddInt64(seq);
        target->InvokeSync(req, 300.0);
        ASSERT_TRUE(req->CheckReturnTypes("l"));
        uint64_t ret = req->GetReturn()->GetValue(0)._intval64;
        EXPECT_EQ(ret, seq + 1);
        seq = ret;
    };
    size_t loop_cnt = 8;
    BenchmarkTimer::benchmark(invoke, invoke, 0.5);
    BenchmarkTimer timer(budget);
    while (timer.has_budget()) {
        timer.before();
        for (size_t i = 0; i < loop_cnt; ++i) {
            invoke();
        }
        timer.after();
    }
    double t = timer.min_time();
    BenchmarkTimer::benchmark(invoke, invoke, 0.5);
    EXPECT_GE(seq, loop_cnt);
    result.req_per_sec[thread_id] = double(loop_cnt) / t;
    req->internal_subref();
    target->internal_subref();
    latch.arrive_and_wait();
    if (thread_id == 0) {
        result.print();
    }
}

void perform_mt_test(size_t num_threads, size_t transport_threads, std::shared_ptr<CryptoEngine> crypto,
                     bool drop_empty_buffers, bool vital = false) {
    Server server(crypto, transport_threads, drop_empty_buffers);
    ASSERT_NO_FATAL_FAILURE(server.start());
    Client client(crypto, transport_threads, server, drop_empty_buffers);
    ASSERT_NO_FATAL_FAILURE(client.start());
    Result     result(num_threads);
    std::latch latch(num_threads);
    auto       task = [&client, &result, &latch, vital](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        perform_test(thread_id, latch, client, result, vital);
    };
    Nexus::run(num_threads, task);
}

CryptoEngine::SP null_crypto = std::make_shared<NullCryptoEngine>();
CryptoEngine::SP tls_crypto =
    std::make_shared<vespalib::TlsCryptoEngine>(vespalib::test::make_tls_options_for_testing());
namespace {
uint32_t getNumThreads() { return std::max(4u, std::thread::hardware_concurrency()); }
} // namespace

TEST(ParallelRpcTest, parallel_rpc_with_1_1_transport_threads_and_num_cores_user_threads_no_encryption) {
    perform_mt_test(getNumThreads(), 1, null_crypto, false);
}

TEST(ParallelRpcTest, parallel_rpc_with_1_1_transport_threads_and_num_cores_user_threads_tls_encryption) {
    perform_mt_test(getNumThreads(), 1, tls_crypto, false);
}

TEST(ParallelRpcTest,
     parallel_rpc_with_1_1_transport_threads_and_num_cores_user_threads_tls_encryption_and_drop_empty_buffers) {
    perform_mt_test(getNumThreads(), 1, tls_crypto, true);
}

TEST(ParallelRpcTest, parallel_rpc_with_8_8_transport_threads_and_num_cores_user_threads_no_encryption) {
    perform_mt_test(getNumThreads(), 8, null_crypto, false, true);
}

TEST(ParallelRpcTest, parallel_rpc_with_8_8_transport_threads_and_num_cores_user_threads_tls_encryption) {
    perform_mt_test(getNumThreads(), 8, tls_crypto, false, true);
}

TEST(ParallelRpcTest,
     parallel_rpc_with_8_8_transport_threads_and_num_cores_user_threads_tls_encryption_and_drop_empty_buffers) {
    perform_mt_test(getNumThreads(), 8, tls_crypto, true);
}

//-----------------------------------------------------------------------------

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    if ((argc == 2) && (argv[1] == std::string("verbose"))) {
        verbose = true;
        budget = 10.0;
    }
    return RUN_ALL_TESTS();
}
