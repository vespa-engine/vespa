// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/intermediatesession.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>

using namespace mbus;
using namespace std::chrono_literals;

struct Handler : public IMessageHandler
{
    DestinationSession::UP session;
    uint32_t               cnt;

    Handler(MessageBus &mb) : session(), cnt(0) {
        session = mb.createDestinationSession("session", true, *this);
    }
    ~Handler() {
        session.reset();
    }
    void handleMessage(Message::UP msg) override {
        ++cnt;
        session->acknowledge(std::move(msg));
    }
};

RoutingSpec getRouting() {
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("dst", "test/*/session"))
                  .addRoute(RouteSpec("test").addHop("dst")));
}

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("loadbalance_test");

    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);
    TestServer  dst1(Identity("test/dst1"), getRouting(), slobrok);
    TestServer  dst2(Identity("test/dst2"), getRouting(), slobrok);
    TestServer  dst3(Identity("test/dst3"), getRouting(), slobrok);

    Handler h1(dst1.mb);
    Handler h2(dst2.mb);
    Handler h3(dst3.mb);

    ASSERT_TRUE(src.waitSlobrok("test/dst1/session"));
    ASSERT_TRUE(src.waitSlobrok("test/dst2/session"));
    ASSERT_TRUE(src.waitSlobrok("test/dst3/session"));

    RoutableQueue queue;
    SourceSessionParams params;
    params.setTimeout(30s);
    params.setThrottlePolicy(IThrottlePolicy::SP());
    SourceSession::UP ss = src.mb.createSourceSession(queue, params);

    uint32_t msgCnt = 90;
    ASSERT_TRUE(msgCnt % 3 == 0);
    for (uint32_t i = 0; i < msgCnt; ++i) {
        ss->send(Message::UP(new SimpleMessage("test")), "test");
    }
    for (uint32_t i = 0; i < 1000; ++i) {
        if (queue.size() == msgCnt) {
            break;
        }
        FastOS_Thread::Sleep(10);
    }
    EXPECT_TRUE(queue.size() == msgCnt);
    EXPECT_TRUE(h1.cnt == msgCnt / 3);
    EXPECT_TRUE(h2.cnt == msgCnt / 3);
    EXPECT_TRUE(h3.cnt == msgCnt / 3);
    TEST_DONE();
}
