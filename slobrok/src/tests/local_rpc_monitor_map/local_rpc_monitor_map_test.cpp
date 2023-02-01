// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/slobrok/server/local_rpc_monitor_map.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/fnet/scheduler.h>
#include <map>

using namespace vespalib;
using namespace slobrok;
using vespalib::make_string_short::fmt;

struct MapCall {
    vespalib::string name;
    ServiceMapping mapping;
    ServiceMapping old;
    static MapCall add(const ServiceMapping &m) { return {"add", m, {"",""}}; }
    static MapCall remove(const ServiceMapping &m) { return {"remove", m, {"",""}}; }
    static MapCall update(const ServiceMapping &o, const ServiceMapping &m) { return {"update", m, o}; }
    void check(const MapCall &rhs) const {
        EXPECT_EQ(name, rhs.name);
        EXPECT_EQ(mapping, rhs.mapping);
        EXPECT_EQ(old, rhs.old);
    }
    ~MapCall();
};
MapCall::~MapCall() = default;

struct MonitorCall {
    vespalib::string name;
    ServiceMapping mapping;
    bool hurry;
    static MonitorCall start(const ServiceMapping &m, bool h) { return {"start", m, h}; }
    static MonitorCall stop(const ServiceMapping &m) { return {"stop", m, false}; }
    void check(const MonitorCall &rhs) const {
        EXPECT_EQ(name, rhs.name);
        EXPECT_EQ(mapping, rhs.mapping);
        EXPECT_EQ(hurry, rhs.hurry);
    }
    ~MonitorCall();
};
MonitorCall::~MonitorCall() = default;

template <typename Call>
class CallLog {
private:
    std::vector<Call> _calls;
    size_t _checked;
public:
    CallLog() noexcept : _calls(), _checked(0) {}
    ~CallLog() { EXPECT_EQ(_calls.size(), _checked); }
    void log(Call call) { _calls.push_back(call); }
    void expect(std::initializer_list<Call> list) {
        ASSERT_EQ(list.size(), (_calls.size() - _checked));
        for (const auto &call: list) {
            call.check(_calls[_checked++]);
        }
    }
};

struct MapLog : CallLog<MapCall>, MapListener {
    ~MapLog() override;
    void add(const ServiceMapping &mapping) override {
        log(MapCall::add(mapping));
    }
    void remove(const ServiceMapping &mapping) override {
        log(MapCall::remove(mapping));
    }
    void update(const ServiceMapping &old_mapping,
                const ServiceMapping &new_mapping) override
    {
        log(MapCall::update(old_mapping, new_mapping));
    }
};

MapLog::~MapLog() = default;

struct MonitorLog : CallLog<MonitorCall>, MappingMonitor {
    void start(const ServiceMapping& mapping, bool hurry) override {
        log(MonitorCall::start(mapping, hurry));
    }
    void stop(const ServiceMapping& mapping) override {
        log(MonitorCall::stop(mapping));
    }
};

struct MyMappingMonitor : MappingMonitor {
    MonitorLog &monitor;
    explicit MyMappingMonitor(MonitorLog &m) : monitor(m) {}
    void start(const ServiceMapping& mapping, bool hurry) override {
        monitor.start(mapping, hurry);
    }
    void stop(const ServiceMapping& mapping) override {
        monitor.stop(mapping);
    }
};

struct LocalRpcMonitorMapTest : public ::testing::Test {
    steady_time time;
    FNET_Scheduler scheduler;
    MonitorLog monitor_log;
    MapLog map_log;
    LocalRpcMonitorMap map;
    std::unique_ptr<MapSubscription> subscription;
    ServiceMapping mapping;
    ServiceMapping mapping_conflict;
    LocalRpcMonitorMapTest()
      : time(duration::zero()),
        scheduler(&time), monitor_log(), map_log(),
        map(&scheduler, [this](auto &owner)
            {
                EXPECT_EQ(&owner, &map);
                return std::make_unique<MyMappingMonitor>(monitor_log);
            }),
        subscription(MapSubscription::subscribe(map.dispatcher(), map_log)),
        mapping("dummy_service", "dummy_spec"),
        mapping_conflict("dummy_service", "conflicting_dummy_spec")
    {}
    void tick(duration elapsed = FNET_Scheduler::tick_ms) {
        time += elapsed;
        scheduler.CheckTasks();
    }
    void add_mapping(const ServiceMapping &m, bool is_up) {
        map.add(m); // <- add from consensus map
        monitor_log.expect({});
        tick(0ms); // <- process delayed add event
        monitor_log.expect({MonitorCall::start(m, false)});
        map_log.expect({});
        if (is_up) {
            map.up(m); // <- up from monitor
            map_log.expect({MapCall::add(m)});
        } else {
            map.down(m); // <- down from monitor
            map_log.expect({});
        }
    }
    void flip_up_state(const ServiceMapping &m, bool was_up, size_t cnt) {
        for (size_t i = 0; i < cnt; ++i) {
            if (was_up) {
                map.up(m);
                map_log.expect({});
                map.down(m);
                map_log.expect({MapCall::remove(m)});
            } else {
                map.down(m);
                map_log.expect({});
                map.up(m);
                map_log.expect({MapCall::add(m)});
            }
            was_up = !was_up;
        }
        monitor_log.expect({});
    }
    void remove_mapping(const ServiceMapping &m, bool was_up) {
        map.remove(m); // <- remove from consensus map
        monitor_log.expect({});
        tick(0ms); // <- process delayed remove event
        monitor_log.expect({MonitorCall::stop(m)});
        if (was_up) {
            map_log.expect({MapCall::remove(m)});
        } else {
            map_log.expect({});
        }
    }
    ~LocalRpcMonitorMapTest() override;
};
LocalRpcMonitorMapTest::~LocalRpcMonitorMapTest() = default;

struct MyAddLocalHandler : CompletionHandler {
    std::unique_ptr<OkState> &state;
    bool &handler_deleted;
    MyAddLocalHandler(std::unique_ptr<OkState> &s, bool &hd)
      : state(s), handler_deleted(hd) {} 
    void doneHandler(OkState result) override {
        state = std::make_unique<OkState>(result);
    }
    ~MyAddLocalHandler() override {
        handler_deleted = true;
    }
};

TEST_F(LocalRpcMonitorMapTest, external_add_remove_while_up) {
    add_mapping(mapping, true);
    remove_mapping(mapping, true);
}

TEST_F(LocalRpcMonitorMapTest, external_add_remove_while_down) {
    add_mapping(mapping, false);
    remove_mapping(mapping, false);
}

TEST_F(LocalRpcMonitorMapTest, server_up_down_up_down) {
    add_mapping(mapping, true);
    flip_up_state(mapping, true, 3);
    remove_mapping(mapping, false);
}

TEST_F(LocalRpcMonitorMapTest, server_down_up_down_up) {
    add_mapping(mapping, false);
    flip_up_state(mapping, false, 3);
    remove_mapping(mapping, true);
}

TEST_F(LocalRpcMonitorMapTest, multi_mapping) {
    ServiceMapping m1("dummy_service1", "dummy_spec1");
    ServiceMapping m2("dummy_service2", "dummy_spec2");
    ServiceMapping m3("dummy_service3", "dummy_spec3");
    add_mapping(m1, true);
    add_mapping(m2, false);
    add_mapping(m3, true);
    flip_up_state(m1, true, 3);
    flip_up_state(m2, false, 3);
    flip_up_state(m3, true, 3);
    remove_mapping(m1, false);
    remove_mapping(m2, true);
    remove_mapping(m3, false);
}

TEST_F(LocalRpcMonitorMapTest, local_add_ok) {
    std::unique_ptr<OkState> state;
    bool handler_deleted;
    map.addLocal(mapping, std::make_unique<MyAddLocalHandler>(state, handler_deleted));
    monitor_log.expect({MonitorCall::start(mapping, true)});
    map_log.expect({});
    map.up(mapping);
    monitor_log.expect({});
    map_log.expect({MapCall::add(mapping)});
    ASSERT_TRUE(state);
    EXPECT_TRUE(state->ok());
    ASSERT_TRUE(handler_deleted);
}

TEST_F(LocalRpcMonitorMapTest, local_add_already_up) {
    std::unique_ptr<OkState> state;
    bool handler_deleted;
    add_mapping(mapping, true);
    map.addLocal(mapping, std::make_unique<MyAddLocalHandler>(state, handler_deleted));
    monitor_log.expect({});
    map_log.expect({});
    ASSERT_TRUE(state);
    EXPECT_TRUE(state->ok());
    ASSERT_TRUE(handler_deleted);
}

TEST_F(LocalRpcMonitorMapTest, local_add_unknown_comes_up) {
    std::unique_ptr<OkState> state;
    bool handler_deleted;
    add_mapping(mapping, false);
    map.addLocal(mapping, std::make_unique<MyAddLocalHandler>(state, handler_deleted));
    monitor_log.expect({MonitorCall::stop(mapping), MonitorCall::start(mapping, true)});
    map_log.expect({});
    EXPECT_FALSE(state);
    map.up(mapping);
    map_log.expect({MapCall::add(mapping)});
    ASSERT_TRUE(state);
    EXPECT_TRUE(state->ok());
    ASSERT_TRUE(handler_deleted);
}

TEST_F(LocalRpcMonitorMapTest, local_add_unknown_goes_down) {
    std::unique_ptr<OkState> state;
    bool handler_deleted;
    add_mapping(mapping, false);
    map.addLocal(mapping, std::make_unique<MyAddLocalHandler>(state, handler_deleted));
    monitor_log.expect({MonitorCall::stop(mapping), MonitorCall::start(mapping, true)});
    map_log.expect({});
    EXPECT_FALSE(state);
    map.down(mapping);
    map_log.expect({});
    ASSERT_TRUE(state);
    EXPECT_FALSE(state->ok());
    ASSERT_TRUE(handler_deleted);
}

TEST_F(LocalRpcMonitorMapTest, local_add_conflict) {
    std::unique_ptr<OkState> state;
    bool handler_deleted;
    add_mapping(mapping, true);
    map.addLocal(mapping_conflict, std::make_unique<MyAddLocalHandler>(state, handler_deleted));
    monitor_log.expect({});
    map_log.expect({});
    ASSERT_TRUE(state);
    EXPECT_TRUE(state->failed());
    ASSERT_TRUE(handler_deleted);
}

TEST_F(LocalRpcMonitorMapTest, local_multi_add) {
    std::unique_ptr<OkState> state1;
    bool handler_deleted1;
    std::unique_ptr<OkState> state2;
    bool handler_deleted2;
    map.addLocal(mapping, std::make_unique<MyAddLocalHandler>(state1, handler_deleted1));
    monitor_log.expect({MonitorCall::start(mapping, true)});
    map.addLocal(mapping, std::make_unique<MyAddLocalHandler>(state2, handler_deleted2));
    monitor_log.expect({});
    map_log.expect({});
    EXPECT_FALSE(state1);
    EXPECT_FALSE(state2);
    map.up(mapping);
    monitor_log.expect({});
    map_log.expect({MapCall::add(mapping)});
    ASSERT_TRUE(state1);
    ASSERT_TRUE(state2);
    EXPECT_TRUE(state1->ok());
    EXPECT_TRUE(state2->ok());
    ASSERT_TRUE(handler_deleted1);
    ASSERT_TRUE(handler_deleted2);
}

TEST_F(LocalRpcMonitorMapTest, local_remove) {
    add_mapping(mapping, true);
    map.removeLocal(mapping);
    monitor_log.expect({MonitorCall::stop(mapping), MonitorCall::start(mapping, false)});
    map_log.expect({MapCall::remove(mapping)});
    map.up(mapping); // timeout case (should normally not happen)
    map_log.expect({MapCall::add(mapping)});
}

TEST_F(LocalRpcMonitorMapTest, local_add_local_remove) {
    std::unique_ptr<OkState> state;
    bool handler_deleted;
    map.addLocal(mapping, std::make_unique<MyAddLocalHandler>(state, handler_deleted));
    monitor_log.expect({MonitorCall::start(mapping, true)});
    map_log.expect({});
    map.removeLocal(mapping);
    monitor_log.expect({MonitorCall::stop(mapping)});
    map_log.expect({});
    ASSERT_TRUE(state);
    EXPECT_TRUE(state->failed());
    ASSERT_TRUE(handler_deleted);
}

GTEST_MAIN_RUN_ALL_TESTS()
