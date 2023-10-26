// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace mbus;

static const duration TIMEOUT = 120s;

TEST("requireThatListenFailedIsExceptionSafe")
{
    fnet::frt::StandaloneFRT orb;
    ASSERT_TRUE(orb.supervisor().Listen(0));

    Slobrok slobrok;
    try {
        TestServer bar(MessageBusParams(),
                       RPCNetworkParams(slobrok.config())
                       .setListenPort(orb.supervisor().GetListenPort()));
        EXPECT_TRUE(false);
    } catch (vespalib::Exception &e) {
        EXPECT_EQUAL("Failed to start network.", e.getMessage());
    }
}

TEST("requireThatShutdownOnSourceWithPendingIsSafe")
{
    Slobrok slobrok;
    TestServer dstServer(MessageBusParams()
                         .addProtocol(std::make_shared<SimpleProtocol>()),
                         RPCNetworkParams(slobrok.config())
                         .setIdentity(Identity("dst")));
    Receptor dstHandler;
    DestinationSession::UP dstSession = dstServer.mb.createDestinationSession(
            DestinationSessionParams()
            .setName("session")
            .setMessageHandler(dstHandler));
    ASSERT_TRUE(dstSession);

    for (uint32_t i = 0; i < 10; ++i) {
        Message::UP msg(new SimpleMessage("msg"));
        {
            TestServer srcServer(MessageBusParams()
                    .setRetryPolicy(std::make_shared<RetryTransientErrorsPolicy>())
                    .addProtocol(std::make_shared<SimpleProtocol>()),
                    RPCNetworkParams(slobrok.config()));
            Receptor srcHandler;
            SourceSession::UP srcSession = srcServer.mb.createSourceSession(SourceSessionParams()
                    .setThrottlePolicy(IThrottlePolicy::SP())
                    .setReplyHandler(srcHandler));
            ASSERT_TRUE(srcSession);
            ASSERT_TRUE(srcServer.waitSlobrok("dst/session", 1));
            ASSERT_TRUE(srcSession->send(std::move(msg), "dst/session", true).isAccepted());
            msg = dstHandler.getMessage(TIMEOUT);
            ASSERT_TRUE(msg);
        }
        dstSession->acknowledge(std::move(msg));
    }
}

TEST("requireThatShutdownOnIntermediateWithPendingIsSafe")
{
    Slobrok slobrok;
    TestServer dstServer(MessageBusParams()
                         .addProtocol(std::make_shared<SimpleProtocol>()),
                         RPCNetworkParams(slobrok.config())
                         .setIdentity(Identity("dst")));
    Receptor dstHandler;
    DestinationSession::UP dstSession = dstServer.mb.createDestinationSession(
            DestinationSessionParams()
            .setName("session")
            .setMessageHandler(dstHandler));
    ASSERT_TRUE(dstSession);

    TestServer srcServer(MessageBusParams()
                         .setRetryPolicy(IRetryPolicy::SP())
                         .addProtocol(std::make_shared<SimpleProtocol>()),
                         RPCNetworkParams(slobrok.config()));
    Receptor srcHandler;
    SourceSession::UP srcSession = srcServer.mb.createSourceSession(SourceSessionParams()
            .setThrottlePolicy(IThrottlePolicy::SP())
            .setReplyHandler(srcHandler));
    ASSERT_TRUE(srcSession);
    ASSERT_TRUE(srcServer.waitSlobrok("dst/session", 1));

    for (uint32_t i = 0; i < 10; ++i) {
        Message::UP msg = std::make_unique<SimpleMessage>("msg");
        {
            TestServer itrServer(MessageBusParams()
                    .setRetryPolicy(std::make_shared<RetryTransientErrorsPolicy>())
                    .addProtocol(std::make_shared<SimpleProtocol>()),
                    RPCNetworkParams(slobrok.config())
                    .setIdentity(Identity("itr")));
            Receptor itrHandler;
            IntermediateSession::UP itrSession = itrServer.mb.createIntermediateSession(
                    IntermediateSessionParams()
                    .setName("session")
                    .setMessageHandler(itrHandler)
                    .setReplyHandler(itrHandler));
            ASSERT_TRUE(itrSession);
            ASSERT_TRUE(srcServer.waitSlobrok("itr/session", 1));
            ASSERT_TRUE(srcSession->send(std::move(msg), "itr/session dst/session", true).isAccepted());
            msg = itrHandler.getMessage(TIMEOUT);
            ASSERT_TRUE(msg);
            itrSession->forward(std::move(msg));
            msg = dstHandler.getMessage(TIMEOUT);
            ASSERT_TRUE(msg);
        }
        ASSERT_TRUE(srcServer.waitSlobrok("itr/session", 0));
        dstSession->acknowledge(std::move(msg));
        dstServer.mb.sync();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
