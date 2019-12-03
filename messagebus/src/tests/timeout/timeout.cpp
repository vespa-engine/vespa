// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/network/identity.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>

using namespace mbus;
using namespace std::chrono_literals;


class Test : public vespalib::TestApp {
public:
    int Main() override;
    void testZeroTimeout();
    void testMessageExpires();
};

TEST_APPHOOK(Test);

int
Test::Main()
{
    TEST_INIT("timeout_test");

    testZeroTimeout();    TEST_FLUSH();
    testMessageExpires(); TEST_FLUSH();

    TEST_DONE();
}

void
Test::testZeroTimeout()
{
    Slobrok slobrok;
    TestServer srcServer(Identity("src"), RoutingSpec(), slobrok);
    TestServer dstServer(Identity("dst"), RoutingSpec(), slobrok);

    Receptor srcHandler;
    SourceSession::UP srcSession = srcServer.mb.createSourceSession(srcHandler, SourceSessionParams().setTimeout(0s));
    Receptor dstHandler;
    DestinationSession::UP dstSession = dstServer.mb.createDestinationSession("session", true, dstHandler);

    ASSERT_TRUE(srcServer.waitSlobrok("dst/session", 1));
    ASSERT_TRUE(srcSession->send(Message::UP(new SimpleMessage("msg")), "dst/session", true).isAccepted());

    Reply::UP reply = srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::TIMEOUT, reply->getError(0).getCode());
}

void
Test::testMessageExpires()
{
    Slobrok slobrok;
    TestServer srcServer(Identity("src"), RoutingSpec(), slobrok);
    TestServer dstServer(Identity("dst"), RoutingSpec(), slobrok);

    Receptor srcHandler, dstHandler;
    SourceSession::UP srcSession = srcServer.mb.createSourceSession(srcHandler, SourceSessionParams().setTimeout(1s));
    DestinationSession::UP dstSession = dstServer.mb.createDestinationSession("session", true, dstHandler);

    ASSERT_TRUE(srcServer.waitSlobrok("dst/session", 1));
    ASSERT_TRUE(srcSession->send(Message::UP(new SimpleMessage("msg")), "dst/session", true).isAccepted());

    Reply::UP reply = srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::TIMEOUT, reply->getError(0).getCode());

    Message::UP msg = dstHandler.getMessage(1s);
    if (msg) {
        msg->discard();
    }
}
