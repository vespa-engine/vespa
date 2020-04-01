// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/messagebus/network/rpctargetpool.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("targetpool_test");

using namespace mbus;

class PoolTimer : public ITimer {
public:
    uint64_t millis;

    PoolTimer() : millis(0) {
        // empty
    }

    uint64_t getMilliTime() const override {
        return millis;
    }
};

TEST("targetpool_test") {

    // Necessary setup to be able to resolve targets.
    Slobrok slobrok;
    TestServer srv1(Identity("srv1"), RoutingSpec(), slobrok);
    RPCServiceAddress adr1("", srv1.mb.getConnectionSpec());
    TestServer srv2(Identity("srv2"), RoutingSpec(), slobrok);
    RPCServiceAddress adr2("", srv2.mb.getConnectionSpec());
    TestServer srv3(Identity("srv3"), RoutingSpec(), slobrok);
    RPCServiceAddress adr3("", srv3.mb.getConnectionSpec());

    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & orb = server.supervisor();
    std::unique_ptr<PoolTimer> ptr(new PoolTimer());
    PoolTimer &timer = *ptr;
    RPCTargetPool pool(std::move(ptr), 0.666);

    // Assert that all connections expire.
    RPCTarget::SP target;
    ASSERT_TRUE((target = pool.getTarget(orb, adr1))); target.reset();
    ASSERT_TRUE((target = pool.getTarget(orb, adr2))); target.reset();
    ASSERT_TRUE((target = pool.getTarget(orb, adr3))); target.reset();
    EXPECT_EQUAL(3u, pool.size());
    for (uint32_t i = 0; i < 10; ++i) {
        pool.flushTargets(false);
        EXPECT_EQUAL(3u, pool.size());
    }
    timer.millis += 999;
    pool.flushTargets(false);
    EXPECT_EQUAL(0u, pool.size());

    // Assert that only idle connections expire.
    ASSERT_TRUE((target = pool.getTarget(orb, adr1))); target.reset();
    ASSERT_TRUE((target = pool.getTarget(orb, adr2))); target.reset();
    ASSERT_TRUE((target = pool.getTarget(orb, adr3))); target.reset();
    EXPECT_EQUAL(3u, pool.size());
    timer.millis += 444;
    pool.flushTargets(false);
    EXPECT_EQUAL(3u, pool.size());
    ASSERT_TRUE((target = pool.getTarget(orb, adr2))); target.reset();
    ASSERT_TRUE((target = pool.getTarget(orb, adr3))); target.reset();
    timer.millis += 444;
    pool.flushTargets(false);
    EXPECT_EQUAL(2u, pool.size());
    ASSERT_TRUE((target = pool.getTarget(orb, adr3))); target.reset();
    timer.millis += 444;
    pool.flushTargets(false);
    EXPECT_EQUAL(1u, pool.size());
    timer.millis += 444;
    pool.flushTargets(false);
    EXPECT_EQUAL(0u, pool.size());

    // Assert that connections never expire while they are referenced.
    ASSERT_TRUE((target = pool.getTarget(orb, adr1)));
    EXPECT_EQUAL(1u, pool.size());
    for (int i = 0; i < 10; ++i) {
        timer.millis += 999;
        pool.flushTargets(false);
        EXPECT_EQUAL(1u, pool.size());
    }
    target.reset();
    timer.millis += 999;
    pool.flushTargets(false);
    EXPECT_EQUAL(0u, pool.size());
}

TEST_MAIN() { TEST_RUN_ALL(); }