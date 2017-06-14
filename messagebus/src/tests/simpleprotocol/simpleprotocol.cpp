// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/ireplyhandler.h>
#include <vespa/messagebus/network/identity.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/component/vtag.h>

using namespace mbus;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("simpleprotocol_test");

    vespalib::Version version = vespalib::Vtag::currentVersion;
    SimpleProtocol protocol;
    EXPECT_TRUE(protocol.getName() == "Simple");

    {
        // test protocol
        IRoutingPolicy::UP bogus = protocol.createPolicy("bogus", "");
        EXPECT_TRUE(bogus.get() == 0);
    }
    TEST_FLUSH();
    {
        // test SimpleMessage
        Message::UP msg(new SimpleMessage("test"));
        EXPECT_TRUE(!msg->isReply());
        EXPECT_TRUE(msg->getProtocol() == SimpleProtocol::NAME);
        EXPECT_TRUE(msg->getType() == SimpleProtocol::MESSAGE);
        EXPECT_TRUE(static_cast<SimpleMessage&>(*msg).getValue() == "test");
        Blob b = protocol.encode(version, *msg);
        EXPECT_TRUE(b.size() > 0);
        Routable::UP tmp = protocol.decode(version, BlobRef(b));
        ASSERT_TRUE(tmp.get() != 0);
        EXPECT_TRUE(!tmp->isReply());
        EXPECT_TRUE(tmp->getProtocol() == SimpleProtocol::NAME);
        EXPECT_TRUE(tmp->getType() == SimpleProtocol::MESSAGE);
        EXPECT_TRUE(static_cast<SimpleMessage&>(*tmp).getValue() == "test");
    }
    TEST_FLUSH();
    {
        // test SimpleReply
        Reply::UP reply(new SimpleReply("reply"));
        EXPECT_TRUE(reply->isReply());
        EXPECT_TRUE(reply->getProtocol() == SimpleProtocol::NAME);
        EXPECT_TRUE(reply->getType() == SimpleProtocol::REPLY);
        EXPECT_TRUE(static_cast<SimpleReply&>(*reply).getValue() == "reply");
        Blob b = protocol.encode(version, *reply);
        EXPECT_TRUE(b.size() > 0);
        Routable::UP tmp = protocol.decode(version, BlobRef(b));
        ASSERT_TRUE(tmp.get() != 0);
        EXPECT_TRUE(tmp->isReply());
        EXPECT_TRUE(tmp->getProtocol() == SimpleProtocol::NAME);
        EXPECT_TRUE(tmp->getType() == SimpleProtocol::REPLY);
        EXPECT_TRUE(static_cast<SimpleReply&>(*tmp).getValue() == "reply");
    }
    TEST_DONE();
}
