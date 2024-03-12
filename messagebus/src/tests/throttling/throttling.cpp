// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/dynamicthrottlepolicy.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/staticthrottlepolicy.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>

using namespace mbus;

////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

class DynamicTimer : public ITimer {
public:
    uint64_t _millis;

    DynamicTimer() : _millis(0) {
        // empty
    }

    uint64_t getMilliTime() const override {
        return _millis;
    }
};

RoutingSpec getRouting()
{
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("dst", "dst/session"))
                  .addRoute(RouteSpec("dst").addHop("dst")));
}

bool waitQueueSize(RoutableQueue &queue, uint32_t size)
{
    for (uint32_t i = 0; i < 10000; ++i) {
        if (queue.size() == size) {
            return true;
        }
        std::this_thread::sleep_for(10ms);
    }
    return false;
}

bool waitPending(SourceSession& session, uint32_t size)
{
    for (uint32_t i = 0; i < 60000; ++i) {
        if (session.getPendingCount() == size) {
            return true;
        }
        std::this_thread::sleep_for(1ms);
    }
    return false;
}

uint32_t
getWindowSize(DynamicThrottlePolicy &policy, DynamicTimer &timer, uint32_t maxPending)
{
    SimpleMessage msg("foo");
    SimpleReply reply("bar");
    reply.setContext(mbus::Context(uint64_t(1))); // To offset pending size bump in static policy

    for (uint32_t i = 0; i < 999; ++i) {
        uint32_t numPending = 0;
        while (policy.canSend(msg, numPending)) {
            policy.processMessage(msg);
            ++numPending;
        }

        uint64_t tripTime = (numPending < maxPending) ? 1000 : 1000 + (numPending - maxPending) * 1000;
        timer._millis += tripTime;

        for( ; numPending > 0 ; --numPending) {
            policy.processReply(reply);
        }
    }
    uint32_t ret = policy.getMaxPendingCount();
    fprintf(stderr, "getWindowSize() = %u\n", ret);
    return ret;
}

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

TEST(ThrottlingTest, test_max_pending_count)
{
    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);
    TestServer  dst(Identity("dst"), getRouting(), slobrok);

    RoutableQueue srcQ;
    RoutableQueue dstQ;

    SourceSessionParams params;
    StaticThrottlePolicy::SP policy(new StaticThrottlePolicy());
    policy->setMaxPendingCount(5);
    policy->setMaxPendingSize(0); // unlimited
    params.setThrottlePolicy(policy);

    SourceSession::UP      ss = src.mb.createSourceSession(srcQ, params);
    DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dstQ);

    ASSERT_TRUE(src.waitSlobrok("dst/session"));

    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());
    }
    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());

    EXPECT_TRUE(waitQueueSize(dstQ, 5));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 1));

    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());
    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());

    EXPECT_TRUE(waitQueueSize(dstQ, 5));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 3));

    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());
    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());
    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());
    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());

    EXPECT_TRUE(waitQueueSize(dstQ, 5));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 8));
    ASSERT_TRUE(waitQueueSize(dstQ, 0));
}

TEST(ThrottlingTest, test_max_pending_size)
{
    ASSERT_TRUE(SimpleMessage("1234567890").getApproxSize() == 10);
    ASSERT_TRUE(SimpleMessage("123456").getApproxSize() == 6);
    ASSERT_TRUE(SimpleMessage("12345").getApproxSize() == 5);
    ASSERT_TRUE(SimpleMessage("1").getApproxSize() == 1);
    ASSERT_TRUE(SimpleMessage("").getApproxSize() == 0);

    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);
    TestServer  dst(Identity("dst"), getRouting(), slobrok);

    RoutableQueue srcQ;
    RoutableQueue dstQ;

    SourceSessionParams params;
    StaticThrottlePolicy::SP policy(new StaticThrottlePolicy());
    policy->setMaxPendingCount(0); // unlimited
    policy->setMaxPendingSize(2);
    params.setThrottlePolicy(policy);

    SourceSession::UP      ss = src.mb.createSourceSession(srcQ, params);
    DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dstQ);

    ASSERT_TRUE(src.waitSlobrok("dst/session"));
    EXPECT_EQ(1u, SimpleMessage("1").getApproxSize());
    EXPECT_EQ(2u, SimpleMessage("12").getApproxSize());

    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("1")), "dst").isAccepted());
    EXPECT_TRUE(waitQueueSize(dstQ, 1));
    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("12")), "dst").isAccepted());
    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("1")), "dst").isAccepted());

    EXPECT_TRUE(waitQueueSize(dstQ, 2));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 1));

    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("1")), "dst").isAccepted());
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 2));

    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("12")), "dst").isAccepted());
    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("1")), "dst").isAccepted());
    EXPECT_TRUE(waitQueueSize(dstQ, 1));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 3));
}

TEST(ThrottlingTest, test_min_one)
{
    ASSERT_TRUE(SimpleMessage("1234567890").getApproxSize() == 10);
    ASSERT_TRUE(SimpleMessage("").getApproxSize() == 0);

    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);
    TestServer  dst(Identity("dst"), getRouting(), slobrok);

    RoutableQueue srcQ;
    RoutableQueue dstQ;

    SourceSessionParams params;
    StaticThrottlePolicy::SP policy(new StaticThrottlePolicy());
    policy->setMaxPendingCount(0); // unlimited
    policy->setMaxPendingSize(5);
    params.setThrottlePolicy(policy);

    SourceSession::UP      ss = src.mb.createSourceSession(srcQ, params);
    DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dstQ);

    ASSERT_TRUE(src.waitSlobrok("dst/session"));

    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("1234567890")), "dst").isAccepted());
    EXPECT_TRUE(!ss->send(Message::UP(new SimpleMessage("")), "dst").isAccepted());

    EXPECT_TRUE(waitQueueSize(dstQ, 1));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 1));
    EXPECT_TRUE(waitQueueSize(dstQ, 0));
}


TEST(ThrottlingTest, test_dynamic_window_size)
{
    auto ptr = std::make_unique<DynamicTimer>();
    auto* timer = ptr.get();
    DynamicThrottlePolicy policy(std::move(ptr));

    policy.setWindowSizeIncrement(5)
          .setResizeRate(1);

    double windowSize = getWindowSize(policy, *timer, 100);
    ASSERT_TRUE(windowSize >= 90 && windowSize <= 105);

    windowSize = getWindowSize(policy, *timer, 200);
    ASSERT_TRUE(windowSize >= 180 && windowSize <= 205);

    windowSize = getWindowSize(policy, *timer, 50);
    ASSERT_TRUE(windowSize >= 45 && windowSize <= 55);

    windowSize = getWindowSize(policy, *timer, 500);
    ASSERT_TRUE(windowSize >= 450 && windowSize <= 505);

    windowSize = getWindowSize(policy, *timer, 100);
    ASSERT_TRUE(windowSize >= 90 && windowSize <= 115);
}

TEST(ThrottlingTest, test_idle_time_period)
{
    auto ptr = std::make_unique<DynamicTimer>();
    auto* timer = ptr.get();
    DynamicThrottlePolicy policy(std::move(ptr));

    policy.setWindowSizeIncrement(5)
          .setMinWindowSize(1)
          .setResizeRate(1);

    double windowSize = getWindowSize(policy, *timer, 100);
    ASSERT_TRUE(windowSize >= 90 && windowSize <= 110);

    SimpleMessage msg("foo");
    timer->_millis += 30001;
    ASSERT_TRUE(policy.canSend(msg, 0));
    ASSERT_TRUE(windowSize >= 90 && windowSize <= 110);

    timer->_millis += 60001;
    ASSERT_TRUE(policy.canSend(msg, 50));
    EXPECT_EQ(55u, policy.getMaxPendingCount());

    timer->_millis += 60001;
    ASSERT_TRUE(policy.canSend(msg, 0));
    EXPECT_EQ(5u, policy.getMaxPendingCount());
}

TEST(ThrottlingTest, test_min_window_size)
{
    auto ptr = std::make_unique<DynamicTimer>();
    auto* timer = ptr.get();
    DynamicThrottlePolicy policy(std::move(ptr));

    policy.setWindowSizeIncrement(5)
          .setResizeRate(1)
          .setMinWindowSize(150);

    double windowSize = getWindowSize(policy, *timer, 200);
    ASSERT_TRUE(windowSize >= 150 && windowSize <= 210);
}

TEST(ThrottlingTest, test_max_window_size)
{
    auto ptr = std::make_unique<DynamicTimer>();
    auto* timer = ptr.get();
    DynamicThrottlePolicy policy(std::move(ptr));

    policy.setWindowSizeIncrement(5)
          .setResizeRate(1)
          .setMaxWindowSize(50);

    double windowSize = getWindowSize(policy, *timer, 100);
    ASSERT_TRUE(windowSize >= 40 && windowSize <= 50);

    policy.setMaxPendingCount(15);
    windowSize = getWindowSize(policy, *timer, 100);
    ASSERT_TRUE(windowSize >= 10 && windowSize <= 15);

}

GTEST_MAIN_RUN_ALL_TESTS()
