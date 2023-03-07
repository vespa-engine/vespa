// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/slobrok/server/rpc_mapping_monitor.h>
#include <vespa/fnet/transport_debugger.h>
#include <vespa/fnet/transport_thread.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <map>

using namespace vespalib;
using namespace slobrok;
using vespalib::make_string_short::fmt;

// simple rpc server implementing the required slobrok call-back API
struct Server : FRT_Invokable {
    fnet::frt::StandaloneFRT frt;
    std::vector<vespalib::string> names;
    size_t inject_fail_cnt;
    FNET_Connection *last_conn;
    void set_last_conn(FNET_Connection *conn) {
        if (last_conn) {
            last_conn->internal_subref();
        }
        last_conn = conn;
        if (last_conn) {
            last_conn->internal_addref();
        }
    }
    Server(fnet::TimeTools::SP time_tools)
        : frt(fnet::TransportConfig().time_tools(time_tools)),
          names(),
          inject_fail_cnt(0),
          last_conn(nullptr)
    {
        FRT_ReflectionBuilder rb(&frt.supervisor());
        rb.DefineMethod("slobrok.callback.listNamesServed", "", "S", FRT_METHOD(Server::rpc_listNamesServed), this);
        rb.DefineMethod("slobrok.callback.notifyUnregistered", "s", "", FRT_METHOD(Server::rpc_notifyUnregistered), this);
        REQUIRE(frt.supervisor().Listen(0));
    }
    ~Server() { set_last_conn(nullptr); }
    vespalib::string spec() const { return fmt("tcp/%d", frt.supervisor().GetListenPort()); }
    FNET_Transport &transport() { return *frt.supervisor().GetTransport(); }
    void rpc_listNamesServed(FRT_RPCRequest *req) {
        set_last_conn(req->GetConnection());
        if (inject_fail_cnt > 0) {
            req->SetError(FRTE_RPC_METHOD_FAILED, "fail injected by unit test");
            --inject_fail_cnt;
        } else {
            FRT_Values &dst = *req->GetReturn();
            FRT_StringValue *names_out = dst.AddStringArray(names.size());
            for (size_t i = 0; i < names.size(); ++i) {
                dst.SetString(&names_out[i], names[i].c_str());
            }
        }
    }
    void rpc_notifyUnregistered(FRT_RPCRequest *) {}
};

enum class State { ANY, UP, DOWN };

// Run-Length-Encoded historic state samples for a single service mapping
struct States {
    struct Entry {
        State state;
        size_t cnt;
    };
    std::vector<Entry> hist;
    State state() const { return hist.back().state; }
    States() : hist({{State::ANY, 0}}) {}
    void sample(State state) {
        if (state == hist.back().state) {
            ++hist.back().cnt;
        } else {
            hist.push_back(Entry{state, 1});
        }
    }
    size_t samples(State state = State::ANY) const {
        size_t n = 0;
        for (const auto &entry: hist) {
            if ((entry.state == state) || (state == State::ANY)) {
                n += entry.cnt;
            }
        }
        return n;
    }
};

// history of which call-backs we have gotten so far
struct History : MappingMonitorOwner {
    std::map<ServiceMapping, States> map;
    void up(const ServiceMapping &mapping) override { map[mapping].sample(State::UP); }
    void down(const ServiceMapping &mapping) override { map[mapping].sample(State::DOWN); }
};

struct RpcMappingMonitorTest : public ::testing::Test {
    fnet::TransportDebugger debugger;
    fnet::frt::StandaloneFRT my_frt;
    Server a;
    Server b;
    History hist;
    std::unique_ptr<RpcMappingMonitor> monitor;
    ServiceMapping foo_a;
    ServiceMapping bar_a;
    ServiceMapping baz_b;
    RpcMappingMonitorTest()
      : debugger(),
        my_frt(fnet::TransportConfig().time_tools(debugger.time_tools())),
        a(debugger.time_tools()),
        b(debugger.time_tools()),
        hist(),
        monitor(),
        foo_a("foo", a.spec()),
        bar_a("bar", a.spec()),
        baz_b("baz", b.spec())
    {
        debugger.attach({*my_frt.supervisor().GetTransport(), a.transport(), b.transport()});
        monitor = std::make_unique<RpcMappingMonitor>(my_frt.supervisor(), hist);
        a.names.push_back(foo_a.name);
        a.names.push_back(bar_a.name);
        b.names.push_back(baz_b.name);
    }
    ~RpcMappingMonitorTest() {
        monitor.reset();
        debugger.detach();
    }
};

TEST_F(RpcMappingMonitorTest, services_can_be_monitored) {
    monitor->start(foo_a, false);
    monitor->start(bar_a, false);
    monitor->start(baz_b, false);
    EXPECT_TRUE(debugger.step_until([&]() {
                return ((hist.map[foo_a].samples() >= 3) &&
                        (hist.map[bar_a].samples() >= 3) &&
                        (hist.map[baz_b].samples() >= 3)); }));
    EXPECT_EQ(hist.map[foo_a].samples(State::DOWN), 0);
    EXPECT_EQ(hist.map[bar_a].samples(State::DOWN), 0);
    EXPECT_EQ(hist.map[baz_b].samples(State::DOWN), 0);
}

TEST_F(RpcMappingMonitorTest, hurry_means_faster) {
    monitor->start(foo_a, false);
    monitor->start(baz_b, true);
    auto t0 = debugger.time();
    EXPECT_TRUE(debugger.step_until([&]() {
                return ((hist.map[baz_b].samples() > 0)); }));
    EXPECT_EQ(hist.map[foo_a].samples(), 0);
    auto t1 = debugger.time();
    EXPECT_TRUE(debugger.step_until([&]() {
                return ((hist.map[foo_a].samples() > 0)); }));
    auto t2 = debugger.time();
    fprintf(stderr, "hurry: ~%" PRIu64 " ms, normal: ~%" PRIu64 " ms\n", count_ms(t1-t0), count_ms(t2-t0));
    EXPECT_GT((t2 - t0), 10 * (t1 - t0));
    EXPECT_EQ(hist.map[foo_a].state(), State::UP);
    EXPECT_EQ(hist.map[baz_b].state(), State::UP);
}

TEST_F(RpcMappingMonitorTest, stop_means_stop) {
    monitor->start(foo_a, false);
    monitor->start(baz_b, true);
    EXPECT_TRUE(debugger.step_until([&]() {
                return ((hist.map[baz_b].samples() == 1)); }));
    monitor->stop(baz_b);
    EXPECT_TRUE(debugger.step_until([&]() {
                return ((hist.map[foo_a].samples() == 3)); }));
    EXPECT_EQ(hist.map[baz_b].samples(), 1);
    EXPECT_EQ(hist.map[foo_a].state(), State::UP);
    EXPECT_EQ(hist.map[baz_b].state(), State::UP);
}

TEST_F(RpcMappingMonitorTest, health_checks_may_fail) {
    ServiceMapping bad_spec("foo", "this spec is invalid");
    ServiceMapping failed_ping("foo", a.spec());
    ServiceMapping missing_name("foo", b.spec());
    a.inject_fail_cnt = 2;
    monitor->start(bad_spec, true);
    monitor->start(failed_ping, true);
    monitor->start(missing_name, true);
    EXPECT_TRUE(debugger.step_until([&]() {
                return (hist.map[failed_ping].state() == State::UP); }));
    EXPECT_EQ(hist.map[bad_spec].state(), State::DOWN);
    EXPECT_EQ(hist.map[missing_name].state(), State::DOWN);
    EXPECT_EQ(hist.map[failed_ping].samples(State::DOWN), 2);
    EXPECT_EQ(hist.map[bad_spec].samples(State::UP), 0);
    EXPECT_EQ(hist.map[missing_name].samples(State::UP), 0);
}

TEST_F(RpcMappingMonitorTest, loss_of_idle_connection_is_detected_and_recovered) {
    monitor->start(foo_a, true);
    EXPECT_TRUE(debugger.step_until([&]() {
                return (hist.map[foo_a].state() == State::UP); }));
    ASSERT_TRUE(a.last_conn);
    a.last_conn->Owner()->Close(a.last_conn);
    a.set_last_conn(nullptr);
    EXPECT_TRUE(debugger.step_until([&]() {
                return (hist.map[foo_a].state() == State::DOWN); }));
    // down without new rpc check, will re-connect and come back up
    EXPECT_FALSE(a.last_conn);
    EXPECT_TRUE(debugger.step_until([&]() {
                return (hist.map[foo_a].state() == State::UP); }));
    EXPECT_EQ(hist.map[foo_a].samples(State::DOWN), 1);    
}

TEST_F(RpcMappingMonitorTest, up_connection_is_reused) {
    monitor->start(foo_a, true);
    EXPECT_TRUE(debugger.step_until([&]() { return (a.last_conn); }));
    auto my_conn = a.last_conn;
    a.last_conn = nullptr;
    EXPECT_TRUE(debugger.step_until([&]() { return (a.last_conn); }));
    EXPECT_EQ(a.last_conn, my_conn);
    my_conn->internal_subref();
    EXPECT_EQ(hist.map[foo_a].state(), State::UP);
}

TEST_F(RpcMappingMonitorTest, detect_ping_interval) {
    monitor->start(foo_a, true);
    EXPECT_TRUE(debugger.step_until([&]() { return (a.last_conn); }));
    auto t1 = debugger.time();
    a.set_last_conn(nullptr);
    EXPECT_TRUE(debugger.step_until([&]() { return (a.last_conn); }));
    auto t2 = debugger.time();
    fprintf(stderr, "ping interval: ~%" PRIu64 " ms\n", count_ms(t2-t1));
}

GTEST_MAIN_RUN_ALL_TESTS()
