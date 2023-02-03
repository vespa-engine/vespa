// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

TEST_SETUP(Test);

RoutingSpec getRouting() {
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("pxy", "test/pxy/session"))
                  .addHop(HopSpec("dst", "test/dst/session"))
                  .addRoute(RouteSpec("test").addHop("pxy").addHop("dst")));
}

int
Test::Main()
{
    TEST_INIT("simple-roundtrip_test");

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

    // wait for slobrok registration
    ASSERT_TRUE(srcNet.waitSlobrok("test/pxy/session"));
    ASSERT_TRUE(srcNet.waitSlobrok("test/dst/session"));
    ASSERT_TRUE(pxyNet.waitSlobrok("test/dst/session"));

    // send message on client
    ss->send(std::make_unique<SimpleMessage>("test message"), "test");

    // check message on proxy
    Message::UP msg = pxy.getMessage();
    ASSERT_TRUE(msg);
    EXPECT_TRUE(msg->getProtocol() == SimpleProtocol::NAME);
    EXPECT_TRUE(msg->getType() == SimpleProtocol::MESSAGE);
    EXPECT_TRUE(dynamic_cast<SimpleMessage&>(*msg).getValue() == "test message");

    // forward message on proxy
    dynamic_cast<SimpleMessage&>(*msg).setValue("test message pxy");
    is->forward(std::move(msg));

    // check message on server
    msg = dst.getMessage();
    ASSERT_TRUE(msg);
    EXPECT_TRUE(msg->getProtocol() == SimpleProtocol::NAME);
    EXPECT_TRUE(msg->getType() == SimpleProtocol::MESSAGE);
    EXPECT_TRUE(dynamic_cast<SimpleMessage&>(*msg).getValue() == "test message pxy");

    // send reply on server
    auto sr = std::make_unique<SimpleReply>("test reply");
    msg->swapState(*sr);
    ds->reply(Reply::UP(sr.release()));

    // check reply on proxy
    Reply::UP reply = pxy.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->getProtocol() == SimpleProtocol::NAME);
    EXPECT_TRUE(reply->getType() == SimpleProtocol::REPLY);
    EXPECT_TRUE(dynamic_cast<SimpleReply&>(*reply).getValue() == "test reply");

    // forward reply on proxy
    dynamic_cast<SimpleReply&>(*reply).setValue("test reply pxy");
    is->forward(std::move(reply));

    // check reply on client
    reply = src.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->getProtocol() == SimpleProtocol::NAME);
    EXPECT_TRUE(reply->getType() == SimpleProtocol::REPLY);
    EXPECT_TRUE(dynamic_cast<SimpleReply&>(*reply).getValue() == "test reply pxy");
    TEST_DONE();
}
