// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/error.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/routablequeue.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <thread>

using namespace mbus;

struct DelayedHandler : public IMessageHandler
{
    DestinationSession::UP session;
    uint32_t               delay;

    DelayedHandler(MessageBus &mb, uint32_t d) : session(), delay(d) {
        session = mb.createDestinationSession("session", true, *this);
    }
    ~DelayedHandler() {
        session.reset();
    }
    void handleMessage(Message::UP msg) override {
        // this will block the transport thread in the server messagebus,
        // but that should be ok, as we only want to test the timing in the
        // client messagebus...
        std::this_thread::sleep_for(std::chrono::milliseconds(delay));
        session->acknowledge(std::move(msg));
    }
};

RoutingSpec getRouting() {
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("dst", "dst/session"))
                  .addRoute(RouteSpec("dst").addHop("dst")));
}

RoutingSpec getBadRouting() {
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("dst", "dst/session"))
                  .addRoute(RouteSpec("dst").addHop("dst")));
}

bool waitQueueSize(RoutableQueue &queue, uint32_t size) {
    for (uint32_t i = 0; i < 60000; ++i) {
        if (queue.size() == size) {
            return true;
        }
        std::this_thread::sleep_for(1ms);
    }
    return false;
}

class Test : public vespalib::TestApp
{
public:
    void testSequencing();
    void testResendError();
    void testResendConnDown();
    void testIllegalRoute();
    void testNoServices();
    void testBlockingClose();
    void testNonBlockingClose();
    int Main() override;
};

void
Test::testSequencing()
{
    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);
    TestServer  dst(Identity("dst"), getRouting(), slobrok);

    RoutableQueue srcQ;
    RoutableQueue dstQ;

    SourceSessionParams params;
    params.setThrottlePolicy(IThrottlePolicy::SP());

    SourceSession::UP      ss = src.mb.createSourceSession(srcQ, params);
    DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dstQ);

    ASSERT_TRUE(src.waitSlobrok("dst/session"));

    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("foo", true, 1)), "dst").isAccepted());
    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("foo", true, 2)), "dst").isAccepted());
    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("foo", true, 1)), "dst").isAccepted());
    EXPECT_TRUE(waitQueueSize(dstQ, 2));
    std::this_thread::sleep_for(250ms);
    EXPECT_TRUE(waitQueueSize(dstQ, 2));
    EXPECT_TRUE(waitQueueSize(srcQ, 0));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    EXPECT_TRUE(waitQueueSize(srcQ, 2));
    EXPECT_TRUE(waitQueueSize(dstQ, 1));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 3));
    ASSERT_TRUE(waitQueueSize(dstQ, 0));
}

void
Test::testResendError()
{
    Slobrok slobrok;
    auto retryPolicy = std::make_shared<RetryTransientErrorsPolicy>();
    retryPolicy->setBaseDelay(0);
    TestServer src(MessageBusParams().addProtocol(std::make_shared<SimpleProtocol>()).setRetryPolicy(retryPolicy),
                   RPCNetworkParams(slobrok.config()));
    src.mb.setupRouting(getRouting());
    TestServer dst(Identity("dst"), getRouting(), slobrok);

    RoutableQueue srcQ;
    RoutableQueue dstQ;

    SourceSession::UP      ss = src.mb.createSourceSession(srcQ);
    DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dstQ);

    ASSERT_TRUE(src.waitSlobrok("dst/session"));

    {
        Message::UP msg(new SimpleMessage("foo"));
        msg->getTrace().setLevel(9);
        EXPECT_TRUE(ss->send(std::move(msg), "dst").isAccepted());
    }
    EXPECT_TRUE(waitQueueSize(dstQ, 1));
    {
        Routable::UP r = dstQ.dequeue();
        Reply::UP reply(new EmptyReply());
        r->swapState(*reply);
        reply->addError(Error(ErrorCode::FATAL_ERROR, "error"));
        ds->reply(std::move(reply));
    }
    EXPECT_TRUE(waitQueueSize(srcQ, 1));
    EXPECT_TRUE(waitQueueSize(dstQ, 0));

    {
        Message::UP msg(new SimpleMessage("foo"));
        msg->getTrace().setLevel(9);
        EXPECT_TRUE(ss->send(std::move(msg), "dst").isAccepted());
    }
    EXPECT_TRUE(waitQueueSize(dstQ, 1));
    {
        Routable::UP r = dstQ.dequeue();
        Reply::UP reply(new EmptyReply());
        r->swapState(*reply);
        reply->addError(Error(ErrorCode::TRANSIENT_ERROR, "error"));
        ds->reply(std::move(reply));
    }
    EXPECT_TRUE(waitQueueSize(dstQ, 1));
    EXPECT_TRUE(waitQueueSize(srcQ, 1));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 2));
    ASSERT_TRUE(waitQueueSize(dstQ, 0));
    {
        string trace1 = srcQ.dequeue()->getTrace().toString();
        string trace2 = srcQ.dequeue()->getTrace().toString();
        fprintf(stderr, "\nTRACE DUMP:\n%s\n\n", trace1.c_str());
        fprintf(stderr, "\nTRACE DUMP:\n%s\n\n", trace2.c_str());
    }
}

void
Test::testResendConnDown()
{
    Slobrok slobrok;
    auto retryPolicy = std::make_shared<RetryTransientErrorsPolicy>();
    retryPolicy->setBaseDelay(0);
    TestServer src(MessageBusParams().addProtocol(std::make_shared<SimpleProtocol>()).setRetryPolicy(retryPolicy),
                   RPCNetworkParams(slobrok.config()));
    src.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                               .addHop(HopSpec("dst", "dst2/session"))
                                               .addHop(HopSpec("pxy", "[All]").addRecipient("dst"))
                                               .addRoute(RouteSpec("dst").addHop("pxy"))));
    RoutableQueue srcQ;
    SourceSession::UP ss = src.mb.createSourceSession(srcQ);

    TestServer dst(Identity("dst"), RoutingSpec(), slobrok);
    RoutableQueue dstQ;
    DestinationSession::UP ds = dst.mb.createDestinationSession("session", true, dstQ);
    ASSERT_TRUE(src.waitSlobrok("dst/session", 1));

    {
        TestServer dst2(Identity("dst2"), RoutingSpec(), slobrok);
        RoutableQueue dst2Q;
        DestinationSession::UP ds2 = dst2.mb.createDestinationSession("session", true, dst2Q);
        ASSERT_TRUE(src.waitSlobrok("dst2/session", 1));

        Message::UP msg(new SimpleMessage("foo"));
        msg->getTrace().setLevel(9);
        EXPECT_TRUE(ss->send(std::move(msg), "dst").isAccepted());
        EXPECT_TRUE(waitQueueSize(dst2Q, 1));
        Routable::UP obj = dst2Q.dequeue();
        obj->discard();
        src.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                   .addHop(HopSpec("dst", "dst/session"))));
    } // dst2 goes down, resend with new config

    ASSERT_TRUE(waitQueueSize(dstQ, 1)); // fails
    ASSERT_TRUE(waitQueueSize(srcQ, 0));
    ds->acknowledge(Message::UP((Message*)dstQ.dequeue().release()));
    ASSERT_TRUE(waitQueueSize(srcQ, 1));
    ASSERT_TRUE(waitQueueSize(dstQ, 0));

    string trace = srcQ.dequeue()->getTrace().toString();
    fprintf(stderr, "\nTRACE DUMP:\n%s\n\n", trace.c_str());
}

void
Test::testIllegalRoute()
{
    Slobrok slobrok;
    TestServer src(MessageBusParams()
                   .addProtocol(std::make_shared<SimpleProtocol>())
                   .setRetryPolicy(IRetryPolicy::SP()),
                   RPCNetworkParams(slobrok.config()));
    src.mb.setupRouting(getRouting());

    RoutableQueue srcQ;
    SourceSession::UP ss = src.mb.createSourceSession(srcQ, SourceSessionParams());
    {
        // no such hop
        Message::UP msg(new SimpleMessage("foo"));
        msg->getTrace().setLevel(9);
        msg->setRoute(Route::parse("bogus"));
        EXPECT_TRUE(ss->send(std::move(msg)).isAccepted());
    }
    ASSERT_TRUE(waitQueueSize(srcQ, 1));
    {
        while (srcQ.size() > 0) {
            Routable::UP routable = srcQ.dequeue();
            ASSERT_TRUE(routable->isReply());
            Reply::UP r(static_cast<Reply*>(routable.release()));
            EXPECT_EQUAL(1u, r->getNumErrors());
            EXPECT_EQUAL((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, r->getError(0).getCode());
            string trace = r->getTrace().toString();
            fprintf(stderr, "\nTRACE DUMP:\n%s\n\n", trace.c_str());
        }
    }
}

void
Test::testNoServices()
{
    Slobrok slobrok;
    TestServer src(MessageBusParams()
                   .addProtocol(std::make_shared<SimpleProtocol>())
                   .setRetryPolicy(IRetryPolicy::SP()),
                   RPCNetworkParams(slobrok.config()));
    src.mb.setupRouting(getBadRouting());

    RoutableQueue srcQ;
    SourceSession::UP ss = src.mb.createSourceSession(srcQ);
    {
        // no services for hop
        Message::UP msg(new SimpleMessage("foo"));
        msg->getTrace().setLevel(9);
        EXPECT_TRUE(ss->send(std::move(msg), "dst").isAccepted());
    }
    ASSERT_TRUE(waitQueueSize(srcQ, 1));
    {
        while (srcQ.size() > 0) {
            Routable::UP routable = srcQ.dequeue();
            ASSERT_TRUE(routable->isReply());
            Reply::UP r(static_cast<Reply*>(routable.release()));
            EXPECT_TRUE(r->getNumErrors() == 1);
            EXPECT_TRUE(r->getError(0).getCode() == ErrorCode::NO_ADDRESS_FOR_SERVICE);
            string trace = r->getTrace().toString();
            fprintf(stderr, "\nTRACE DUMP:\n%s\n\n", trace.c_str());
        }
    }
}

void
Test::testBlockingClose()
{
    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);
    TestServer  dst(Identity("dst"), getRouting(), slobrok);

    RoutableQueue  srcQ;
    DelayedHandler dstH(dst.mb, 1000);
    ASSERT_TRUE(src.waitSlobrok("dst/session"));

    SourceSessionParams params;
    SourceSession::UP   ss = src.mb.createSourceSession(srcQ, params);

    EXPECT_TRUE(ss->send(Message::UP(new SimpleMessage("foo")), "dst").isAccepted());
    ss->close();
    srcQ.handleMessage(Message::UP(new SimpleMessage("bogus")));
    Routable::UP routable = srcQ.dequeue();
    EXPECT_TRUE(routable->isReply());
}

void
Test::testNonBlockingClose()
{
    Slobrok     slobrok;
    TestServer  src(Identity(""), getRouting(), slobrok);

    RoutableQueue srcQ;

    SourceSessionParams params;
    SourceSession::UP   ss = src.mb.createSourceSession(srcQ, params);
    ss->close(); // this should not hang
}

int
Test::Main()
{
    TEST_INIT("sourcesession_test");
    testSequencing();       TEST_FLUSH();
    testResendError();      TEST_FLUSH();
    testResendConnDown();   TEST_FLUSH();
    testIllegalRoute();     TEST_FLUSH();
    testNoServices();       TEST_FLUSH();
    testBlockingClose();    TEST_FLUSH();
    testNonBlockingClose(); TEST_FLUSH();
    TEST_DONE();
}

TEST_APPHOOK(Test);
