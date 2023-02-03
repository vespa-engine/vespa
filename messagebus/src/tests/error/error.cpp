// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/intermediatesession.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

RoutingSpec getRouting() {
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("pxy", "test/pxy/session"))
                  .addHop(HopSpec("dst", "test/dst/session"))
                  .addRoute(RouteSpec("test").addHop("pxy").addHop("dst")));
}

TEST("error_test") {

    Slobrok     slobrok;
    TestServer  srcNet(Identity("test/src"), getRouting(), slobrok);
    TestServer  pxyNet(Identity("test/pxy"), getRouting(), slobrok);
    TestServer  dstNet(Identity("test/dst"), getRouting(), slobrok);

    Receptor    src;
    Receptor    pxy;
    Receptor    dst;

    SourceSession::UP       ss = srcNet.mb.createSourceSession(src, SourceSessionParams());
    IntermediateSession::UP is = pxyNet.mb.createIntermediateSession("session", true, pxy, pxy);
    DestinationSession::UP  ds = dstNet.mb.createDestinationSession("session", true, dst);

    ASSERT_TRUE(srcNet.waitSlobrok("test/pxy/session"));
    ASSERT_TRUE(srcNet.waitSlobrok("test/dst/session"));
    ASSERT_TRUE(pxyNet.waitSlobrok("test/dst/session"));

    for (int i = 0; i < 5; i++) {
        ASSERT_TRUE(ss->send(std::make_unique<SimpleMessage>("test message"), "test").isAccepted());
        Message::UP msg = pxy.getMessage();
        ASSERT_TRUE(msg);
        is->forward(std::move(msg));

        msg = dst.getMessage();
        ASSERT_TRUE(msg);
        Reply::UP reply = std::make_unique<EmptyReply>();
        msg->swapState(*reply);
        reply->addError(Error(ErrorCode::APP_FATAL_ERROR, "fatality"));
        ds->reply(std::move(reply));

        reply = pxy.getReply();
        ASSERT_TRUE(reply);
        ASSERT_EQUAL(reply->getNumErrors(), 1u);
        EXPECT_EQUAL(reply->getError(0).getService(), "test/dst/session");
        reply->addError(Error(ErrorCode::APP_FATAL_ERROR, "fatality"));
        is->forward(std::move(reply));

        reply = src.getReply();
        ASSERT_TRUE(reply);
        ASSERT_EQUAL(reply->getNumErrors(), 2u);
        EXPECT_EQUAL(reply->getError(0).getService(), "test/dst/session");
        EXPECT_EQUAL(reply->getError(1).getService(), "test/pxy/session");
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }