// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace ::testing;

namespace mbus {

RoutingSpec getRouting() {
    return RoutingSpec()
        .addTable(RoutingTableSpec("Simple")
                  .addHop(HopSpec("pxy", "test/pxy/session"))
                  .addHop(HopSpec("dst", "test/dst/session"))
                  .addRoute(RouteSpec("test").addHop("pxy").addHop("dst")));
}

struct SimpleRoundtripTest : Test {
    Slobrok     slobrok;
    TestServer  srcNet{Identity("test/src"), getRouting(), slobrok};
    TestServer  pxyNet{Identity("test/pxy"), getRouting(), slobrok};
    TestServer  dstNet{Identity("test/dst"), getRouting(), slobrok};

    Receptor    src;
    Receptor    pxy;
    Receptor    dst;

    SourceSession::UP       ss = srcNet.mb.createSourceSession(src, SourceSessionParams());
    IntermediateSession::UP is = pxyNet.mb.createIntermediateSession("session", true, pxy, pxy);
    DestinationSession::UP  ds = dstNet.mb.createDestinationSession("session", true, dst);

    SimpleRoundtripTest();
    ~SimpleRoundtripTest() override;

    void SetUp() override {
        // wait for slobrok registration
        ASSERT_TRUE(srcNet.waitSlobrok("test/pxy/session"));
        ASSERT_TRUE(srcNet.waitSlobrok("test/dst/session"));
        ASSERT_TRUE(pxyNet.waitSlobrok("test/dst/session"));
    }

    void do_test_header_kvs_are_propagated(const std::optional<std::string>& foo_meta,
                                           const std::optional<std::string>& bar_meta);
};

SimpleRoundtripTest::SimpleRoundtripTest()  = default;
SimpleRoundtripTest::~SimpleRoundtripTest() = default;

TEST_F(SimpleRoundtripTest, simple_roundtrip_test) {
    // send message on client
    ss->send(std::make_unique<SimpleMessage>("test message"), "test");

    // check message on proxy
    Message::UP msg = pxy.getMessage();
    ASSERT_TRUE(msg);
    EXPECT_TRUE(msg->getProtocol() == SimpleProtocol::NAME);
    EXPECT_TRUE(msg->getType() == SimpleProtocol::MESSAGE);
    EXPECT_FALSE(msg->hasMetadata());
    EXPECT_TRUE(dynamic_cast<SimpleMessage&>(*msg).getValue() == "test message");

    // forward message on proxy
    dynamic_cast<SimpleMessage&>(*msg).setValue("test message pxy");
    is->forward(std::move(msg));

    // check message on server
    msg = dst.getMessage();
    ASSERT_TRUE(msg);
    EXPECT_TRUE(msg->getProtocol() == SimpleProtocol::NAME);
    EXPECT_TRUE(msg->getType() == SimpleProtocol::MESSAGE);
    EXPECT_FALSE(msg->hasMetadata());
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
}

void SimpleRoundtripTest::do_test_header_kvs_are_propagated(const std::optional<std::string>& foo_meta,
                                                            const std::optional<std::string>& bar_meta) {
    auto msg_to_send = std::make_unique<SimpleMessage>("test message");
    msg_to_send->set_foo_meta(foo_meta);
    msg_to_send->set_bar_meta(bar_meta);
    const bool has_meta = msg_to_send->hasMetadata();
    ss->send(std::move(msg_to_send), "test");

    Message::UP msg = pxy.getMessage();
    auto* as_simple_msg = dynamic_cast<SimpleMessage*>(msg.get());
    ASSERT_TRUE(as_simple_msg);
    EXPECT_EQ(as_simple_msg->hasMetadata(), has_meta);
    EXPECT_EQ(as_simple_msg->foo_meta(), foo_meta);
    EXPECT_EQ(as_simple_msg->bar_meta(), bar_meta);
    is->forward(std::move(msg));

    msg = dst.getMessage();
    as_simple_msg = dynamic_cast<SimpleMessage*>(msg.get());
    ASSERT_TRUE(as_simple_msg);
    EXPECT_EQ(as_simple_msg->hasMetadata(), has_meta);
    EXPECT_EQ(as_simple_msg->foo_meta(), foo_meta);
    EXPECT_EQ(as_simple_msg->bar_meta(), bar_meta);

    // Avoid dangling messages
    auto sr = std::make_unique<SimpleReply>("test reply");
    msg->swapState(*sr);
    ds->reply(std::move(sr));

    Reply::UP reply = pxy.getReply();
    ASSERT_TRUE(reply);
    is->forward(std::move(reply));

    reply = src.getReply();
    ASSERT_TRUE(reply);
}

TEST_F(SimpleRoundtripTest, empty_kv_map_is_propagated) {
    do_test_header_kvs_are_propagated(std::nullopt, std::nullopt);
}

TEST_F(SimpleRoundtripTest, single_header_kv_is_propagated) {
    do_test_header_kvs_are_propagated("marve", std::nullopt);
}

TEST_F(SimpleRoundtripTest, multiple_header_kvs_are_propagated) {
    do_test_header_kvs_are_propagated("marve", "fleksnes");
}

} // mbus

GTEST_MAIN_RUN_ALL_TESTS()
