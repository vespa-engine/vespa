// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/intermediatesession.h>
#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/testlib/simplemessage.h>

using namespace mbus;

//-----------------------------------------------------------------------------

class Proxy : public IMessageHandler,
              public IReplyHandler
{
private:
    IntermediateSession::UP _session;
public:
    Proxy(MessageBus &bus);
    void handleMessage(Message::UP msg) override;
    void handleReply(Reply::UP reply) override;
};

Proxy::Proxy(MessageBus &bus)
    : _session(bus.createIntermediateSession("session", true, *this, *this))
{
}

void
Proxy::handleMessage(Message::UP msg) {
    msg->getTrace().trace(1, "Proxy message", false);
    _session->forward(std::move(msg));
}

void
Proxy::handleReply(Reply::UP reply) {
    reply->getTrace().trace(1, "Proxy reply", false);
    _session->forward(std::move(reply));
}

//-----------------------------------------------------------------------------

class Server : public IMessageHandler
{
private:
    DestinationSession::UP _session;
public:
    Server(MessageBus &bus);
    void handleMessage(Message::UP msg) override;
};

Server::Server(MessageBus &bus)
    : _session(bus.createDestinationSession("session", true, *this))
{
}

void
Server::handleMessage(Message::UP msg) {
    msg->getTrace().trace(1, "Server message", false);
    Reply::UP reply(new EmptyReply());
    msg->swapState(*reply);
    reply->getTrace().trace(1, "Server reply", false);
    _session->reply(std::move(reply));
}

//-----------------------------------------------------------------------------

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
    Proxy       pxy(pxyNet.mb);
    Server      dst(dstNet.mb);

    SourceSession::UP ss = srcNet.mb.createSourceSession(src, SourceSessionParams());

    // wait for slobrok registration
    ASSERT_TRUE(srcNet.waitSlobrok("test/pxy/session"));
    ASSERT_TRUE(srcNet.waitSlobrok("test/dst/session"));
    ASSERT_TRUE(pxyNet.waitSlobrok("test/dst/session"));

    Message::UP msg(new SimpleMessage(""));
    msg->getTrace().setLevel(1);
    msg->getTrace().trace(1, "Client message", false);
    ss->send(std::move(msg), "test");
    Reply::UP reply = src.getReply();
    reply->getTrace().trace(1, "Client reply", false);
    EXPECT_TRUE(reply->getNumErrors() == 0);

    TraceNode t = TraceNode()
                  .addChild("Client message")
                  .addChild("Proxy message")
                  .addChild("Server message")
                  .addChild("Server reply")
                  .addChild("Proxy reply")
                  .addChild("Client reply");
    EXPECT_TRUE(reply->getTrace().encode() == t.encode());
    TEST_DONE();
}
