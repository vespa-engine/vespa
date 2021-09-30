// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <thread>

using namespace mbus;

struct Handler : public IMessageHandler
{
    DestinationSession::UP session;

    Handler(MessageBus &mb) : session() {
        session = mb.createDestinationSession("session", true, *this);
    }
    ~Handler() override {
        session.reset();
    }
    void handleMessage(Message::UP msg) override {
        session->acknowledge(std::move(msg));
    }
};

RoutingSpec getRouting() {
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("test", "test/session"))
                  .addRoute(RouteSpec("test").addHop("test")));
}

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("context_test");

    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);
    TestServer  dst(Identity("test"), getRouting(), slobrok);
    Handler     handler(dst.mb);

    ASSERT_TRUE(src.waitSlobrok("test/session"));

    RoutableQueue queue;
    SourceSessionParams params;
    params.setThrottlePolicy(IThrottlePolicy::SP());
    SourceSession::UP ss = src.mb.createSourceSession(queue, params);

    {
        Message::UP msg(new SimpleMessage("test", true, 1));
        msg->setContext(Context((uint64_t)10));
        ss->send(std::move(msg), "test");
    }
    {
        Message::UP msg(new SimpleMessage("test", true, 1));
        msg->setContext(Context((uint64_t)20));
        ss->send(std::move(msg), "test");
    }
    {
        Message::UP msg(new SimpleMessage("test", true, 1));
        msg->setContext(Context((uint64_t)30));
        ss->send(std::move(msg), "test");
    }
    for (uint32_t i = 0; i < 1000; ++i) {
        if (queue.size() == 3) {
            break;
        }
        std::this_thread::sleep_for(10ms);
    }
    EXPECT_EQUAL(queue.size(), 3u);
    {
        Reply::UP reply = Reply::UP((Reply*)queue.dequeue().release());
        ASSERT_TRUE(reply);
        EXPECT_EQUAL(reply->getContext().value.UINT64, 10u);
    }
    {
        Reply::UP reply = Reply::UP((Reply*)queue.dequeue().release());
        ASSERT_TRUE(reply);
        EXPECT_EQUAL(reply->getContext().value.UINT64, 20u);
    }
    {
        Reply::UP reply = Reply::UP((Reply*)queue.dequeue().release());
        ASSERT_TRUE(reply);
        EXPECT_EQUAL(reply->getContext().value.UINT64, 30u);
    }
    TEST_DONE();
}
