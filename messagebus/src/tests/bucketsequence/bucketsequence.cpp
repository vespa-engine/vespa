// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

TEST_SETUP(Test);

class MyMessage : public SimpleMessage {
public:
    MyMessage() : SimpleMessage("foo") { }
    bool hasBucketSequence() override { return true; }
};

int
Test::Main()
{
    TEST_INIT("bucketsequence_test");

    Slobrok slobrok;
    TestServer server(MessageBusParams()
                      .addProtocol(IProtocol::SP(new SimpleProtocol()))
                      .setRetryPolicy(IRetryPolicy::SP(new RetryTransientErrorsPolicy())),
                      RPCNetworkParams()
                      .setSlobrokConfig(slobrok.config()));
    Receptor receptor;
    SourceSession::UP session = server.mb.createSourceSession(
            SourceSessionParams()
            .setReplyHandler(receptor));
    Message::UP msg(new MyMessage());
    msg->setRoute(Route::parse("foo"));
    ASSERT_TRUE(session->send(std::move(msg)).isAccepted());
    Reply::UP reply = receptor.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::SEQUENCE_ERROR, reply->getError(0).getCode());

    TEST_DONE();
}
