// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>

using namespace vespalib;

struct Rpc : FRT_Invokable {
    FNET_Transport    transport;
    FRT_Supervisor    orb;
    Rpc(CryptoEngine::SP crypto, size_t num_threads, bool drop_empty)
        : transport(fnet::TransportConfig(num_threads).crypto(std::move(crypto)).drop_empty_buffers(drop_empty)), orb(&transport) {}
    void start() {
        ASSERT_TRUE(transport.Start());
    }
    uint32_t listen() {
        ASSERT_TRUE(orb.Listen(0));
        return orb.GetListenPort();
    }
    FRT_Target *connect(uint32_t port) {
        return orb.GetTarget(port);
    }
    ~Rpc() override {
        transport.ShutDown(true);
    }
};

struct Server : Rpc {
    uint32_t port;
    Server(CryptoEngine::SP crypto, size_t num_threads, bool drop_empty = false) : Rpc(std::move(crypto), num_threads, drop_empty), port(listen()) {
        init_rpc();
        start();
    }
    ~Server() override;
    void init_rpc() {
        FRT_ReflectionBuilder rb(&orb);
        rb.DefineMethod("inc", "l", "l", FRT_METHOD(Server::rpc_inc), this);
        rb.MethodDesc("increment a 64-bit integer");
        rb.ParamDesc("in", "an integer (64 bit)");
        rb.ReturnDesc("out", "in + 1 (64 bit)");
    }
    void rpc_inc(FRT_RPCRequest *req) {
        FRT_Values &params = *req->GetParams();
        FRT_Values &ret    = *req->GetReturn();
        ret.AddInt64(params[0]._intval64 + 1);
    }
};

Server::~Server() = default;

struct Client : Rpc {
    uint32_t port;
    Client(CryptoEngine::SP crypto, size_t num_threads, const Server &server, bool drop_empty = false) : Rpc(std::move(crypto), num_threads, drop_empty), port(server.port) {
        start();
    }
    ~Client() override;
    FRT_Target *connect() { return Rpc::connect(port); }
};

Client::~Client() = default;

struct Result {
    std::vector<double> req_per_sec;
    explicit Result(size_t num_threads) : req_per_sec(num_threads, 0.0) {}
    double throughput() const {
        double sum = 0.0;
        for (double sample: req_per_sec) {
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

bool verbose = false;
double budget = 1.5;

void perform_test(size_t thread_id, Client &client, Result &result, bool vital = false) {
    if (!vital && !verbose) {
        if (thread_id == 0) {
            fprintf(stderr, "... skipping non-vital test; run with 'verbose' to enable\n");
        }
        return;
    }
    uint64_t seq = 0;
    FRT_Target *target = client.connect();
    FRT_RPCRequest *req = client.orb.AllocRPCRequest();
    auto invoke = [&seq, target, &client, &req](){
        req = client.orb.AllocRPCRequest(req);
        req->SetMethodName("inc");
        req->GetParams()->AddInt64(seq);
        target->InvokeSync(req, 300.0);
        ASSERT_TRUE(req->CheckReturnTypes("l"));
        uint64_t ret = req->GetReturn()->GetValue(0)._intval64;
        EXPECT_EQUAL(ret, seq + 1);
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
    EXPECT_GREATER_EQUAL(seq, loop_cnt);
    result.req_per_sec[thread_id] = double(loop_cnt) / t;
    req->SubRef();
    target->SubRef();
    TEST_BARRIER();
    if (thread_id == 0) {
        result.print();
    }
}

CryptoEngine::SP null_crypto = std::make_shared<NullCryptoEngine>();
CryptoEngine::SP tls_crypto = std::make_shared<vespalib::TlsCryptoEngine>(vespalib::test::make_tls_options_for_testing());
namespace {
    uint32_t getNumThreads() {
        return std::max(4u, std::thread::hardware_concurrency());
    }
}

TEST_MT_FFF("parallel rpc with 1/1 transport threads and num_cores user threads (no encryption)",
            getNumThreads(), Server(null_crypto, 1), Client(null_crypto, 1, f1), Result(num_threads)) { perform_test(thread_id, f2, f3); }

TEST_MT_FFF("parallel rpc with 1/1 transport threads and num_cores user threads (tls encryption)",
            getNumThreads(), Server(tls_crypto, 1), Client(tls_crypto, 1, f1), Result(num_threads)) { perform_test(thread_id, f2, f3); }

TEST_MT_FFF("parallel rpc with 1/1 transport threads and num_cores user threads (tls encryption + drop empty buffers)",
            getNumThreads(), Server(tls_crypto, 1, true), Client(tls_crypto, 1, f1, true), Result(num_threads)) { perform_test(thread_id, f2, f3); }

TEST_MT_FFF("parallel rpc with 8/8 transport threads and num_cores user threads (no encryption)",
            getNumThreads(), Server(null_crypto, 8), Client(null_crypto, 8, f1), Result(num_threads)) { perform_test(thread_id, f2, f3, true); }

TEST_MT_FFF("parallel rpc with 8/8 transport threads and num_cores user threads (tls encryption)",
            getNumThreads(), Server(tls_crypto, 8), Client(tls_crypto, 8, f1), Result(num_threads)) { perform_test(thread_id, f2, f3, true); }

TEST_MT_FFF("parallel rpc with 8/8 transport threads and num_cores user threads (tls encryption + drop empty buffers)",
            getNumThreads(), Server(tls_crypto, 8, true), Client(tls_crypto, 8, f1, true), Result(num_threads)) { perform_test(thread_id, f2, f3); }

//-----------------------------------------------------------------------------

int main(int argc, char **argv) {
    TEST_MASTER.init(__FILE__);
    if ((argc == 2) && (argv[1] == std::string("verbose"))) {
        verbose = true;
        budget = 10.0;
    }
    TEST_RUN_ALL();
    return (TEST_MASTER.fini() ? 0 : 1);
}
