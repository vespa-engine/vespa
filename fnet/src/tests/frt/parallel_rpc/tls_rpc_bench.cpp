// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/test/time_tracer.h>
#include <vespa/vespalib/util/benchmark_timer.h>

#include <chrono>
#include <thread>

using namespace vespalib;

using vespalib::test::TimeTracer;

CryptoEngine::SP null_crypto = std::make_shared<NullCryptoEngine>();
CryptoEngine::SP tls_crypto =
    std::make_shared<vespalib::TlsCryptoEngine>(vespalib::test::make_tls_options_for_testing());

TT_Tag req_tag("request");

struct Fixture : FRT_Invokable {
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor&          orb;
    Fixture(CryptoEngine::SP crypto) : server(std::move(crypto)), orb(server.supervisor()) {}
    void start() {
        ASSERT_TRUE(orb.Listen(0));
        init_rpc();
    }
    FRT_Target* connect() { return orb.GetTarget(orb.GetListenPort()); }
    ~Fixture() override;
    void init_rpc() {
        FRT_ReflectionBuilder rb(&orb);
        rb.DefineMethod("inc", "l", "l", FRT_METHOD(Fixture::rpc_inc), this);
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

Fixture::~Fixture() = default;

struct DurationCmp {
    bool operator()(const TimeTracer::Record& a, const TimeTracer::Record& b) {
        return ((a.stop - a.start) < (b.stop - b.start));
    }
};

struct StartCmp {
    bool operator()(const TimeTracer::Record& a, const TimeTracer::Record& b) { return (a.start < b.start); }
};

std::string get_prefix(const std::vector<TimeTracer::Record>& stats, size_t idx) {
    std::string        prefix;
    TimeTracer::Record self = stats[idx];
    while (idx-- > 0) {
        if (stats[idx].thread_id == self.thread_id) {
            if (stats[idx].stop > self.start) {
                prefix.append("...");
            }
        }
    }
    if (!prefix.empty()) {
        prefix.append(" ");
    }
    return prefix;
}

void benchmark_rpc(Fixture& fixture, bool reconnect) {
    uint64_t        seq = 0;
    FRT_Target*     target = fixture.connect();
    FRT_RPCRequest* req = fixture.orb.AllocRPCRequest();
    auto            invoke = [&seq, &target, &req, &fixture, reconnect]() {
        TT_Sample sample(req_tag);
        if (reconnect) {
            target->internal_subref();
            target = fixture.connect();
        }
        req = fixture.orb.AllocRPCRequest(req);
        req->SetMethodName("inc");
        req->GetParams()->AddInt64(seq);
        target->InvokeSync(req, 60.0);
        ASSERT_TRUE(req->CheckReturnTypes("l"));
        uint64_t ret = req->GetReturn()->GetValue(0)._intval64;
        EXPECT_EQ(ret, seq + 1);
        seq = ret;
    };
    auto   before = TimeTracer::now();
    double t = BenchmarkTimer::benchmark(invoke, 5.0);
    auto   after = TimeTracer::now();
    target->internal_subref();
    req->internal_subref();
    auto stats = TimeTracer::extract().by_time(before, after).by_tag(req_tag.id()).get();
    ASSERT_TRUE(stats.size() > 0);
    std::sort(stats.begin(), stats.end(), DurationCmp());
    auto med_sample = stats[stats.size() / 2];
    fprintf(stderr, "estimated min request latency: %g ms (reconnect = %s)\n", (t * 1000.0),
            reconnect ? "yes" : "no");
    fprintf(stderr, "actual median request latency: %g ms (reconnect = %s)\n", med_sample.ms_duration(),
            reconnect ? "yes" : "no");
    stats = TimeTracer::extract().by_time(med_sample.start, med_sample.stop).get();
    ASSERT_TRUE(stats.size() > 0);
    std::sort(stats.begin(), stats.end(), StartCmp());
    fprintf(stderr, "===== time line BEGIN =====\n");
    for (size_t i = 0; i < stats.size(); ++i) {
        const auto& entry = stats[i];
        double      abs_start = std::chrono::duration<double, std::milli>(entry.start - med_sample.start).count();
        double      abs_stop = std::chrono::duration<double, std::milli>(entry.stop - med_sample.start).count();
        fprintf(stderr, "%s[%g, %g] [%u:%s] %g ms\n", get_prefix(stats, i).c_str(), abs_start, abs_stop,
                entry.thread_id, entry.tag_name().c_str(), entry.ms_duration());
    }
    fprintf(stderr, "===== time line END =====\n");
    std::vector<TimeTracer::Record> high_duration;
    for (const auto& entry : stats) {
        if (entry.ms_duration() > 1.0) {
            high_duration.push_back(entry);
        }
    }
    for (const auto& entry : high_duration) {
        if (entry.tag_id != req_tag.id()) {
            fprintf(stderr, "WARNING: high duration: [%u:%s] %g ms\n", entry.thread_id, entry.tag_name().c_str(),
                    entry.ms_duration());
        }
    }
}

TEST(TlsRpcBenchTest, rpc_with_null_encryption) {
    Fixture f1(null_crypto);
    ASSERT_NO_FATAL_FAILURE(f1.start());
    benchmark_rpc(f1, false);
    benchmark_rpc(f1, true);
}

TEST(TlsRpcBenchTest, rpc_with_tls_encryption) {
    Fixture f1(tls_crypto);
    ASSERT_NO_FATAL_FAILURE(f1.start());
    benchmark_rpc(f1, false);
    benchmark_rpc(f1, true);
}

GTEST_MAIN_RUN_ALL_TESTS()
