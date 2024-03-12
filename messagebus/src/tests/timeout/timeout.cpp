// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/gtest/gtest.h>

using namespace mbus;
using namespace std::chrono_literals;


TEST(TimeoutTest, test_zero_timeout)
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
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::TIMEOUT, reply->getError(0).getCode());
}

TEST(TimeoutTest, test_message_expires)
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
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::TIMEOUT, reply->getError(0).getCode());

    Message::UP msg = dstHandler.getMessage(1s);
    if (msg) {
        msg->discard();
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
