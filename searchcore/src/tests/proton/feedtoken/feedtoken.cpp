// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace proton;

class LocalTransport : public FeedToken::ITransport {
private:
    mbus::Receptor _receptor;
    double         _latency_ms;

public:
    LocalTransport()
        : _receptor(),
          _latency_ms(0.0)
    {
        // empty
    }

    void send(mbus::Reply::UP reply, ResultUP, bool, double latency_ms) override {
        _receptor.handleReply(std::move(reply));
        _latency_ms = latency_ms;
    }

    mbus::Reply::UP getReply() {
        return _receptor.getReply();
    }

    double getLatencyMs() const {
        return _latency_ms;
    }
};

class Test : public vespalib::TestApp {
private:
    void testAck();
    void testAutoReply();
    void testFail();
    void testHandover();
    void testIntegrity();
    void testTrace();

public:
    int Main() override {
        TEST_INIT("feedtoken_test");

        testAck();       TEST_FLUSH();
//        testAutoReply(); TEST_FLUSH();
        testFail();      TEST_FLUSH();
        testHandover();  TEST_FLUSH();
//        testIntegrity(); TEST_FLUSH();
        testTrace();     TEST_FLUSH();

        TEST_DONE();
    }
};

TEST_APPHOOK(Test);

void
Test::testAck()
{
    LocalTransport transport;
    mbus::Reply::UP msg(new documentapi::RemoveDocumentReply());
    FeedToken token(transport, std::move(msg));
    token.ack();
    mbus::Reply::UP reply = transport.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testAutoReply()
{
    mbus::Receptor receptor;
    mbus::Reply::UP reply(new documentapi::RemoveDocumentReply());
    reply->pushHandler(receptor);
    {
        LocalTransport transport;
        FeedToken token(transport, std::move(reply));
    }
    reply = receptor.getReply(0);
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(reply->hasErrors());
}

void
Test::testFail()
{
    LocalTransport transport;
    mbus::Reply::UP reply(new documentapi::RemoveDocumentReply());
    FeedToken token(transport, std::move(reply));
    token.fail(69, "6699");
    reply = transport.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL(69u, reply->getError(0).getCode());
    EXPECT_EQUAL("6699", reply->getError(0).getMessage());
}

void
Test::testHandover()
{
    struct MyHandover {
        static FeedToken handover(FeedToken token) {
            return token;
        }
    };

    LocalTransport transport;
    mbus::Reply::UP reply(new documentapi::RemoveDocumentReply());

    FeedToken token(transport, std::move(reply));
    token = MyHandover::handover(token);
    token.ack();
    reply = transport.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testIntegrity()
{
    LocalTransport transport;
    try {
        FeedToken token(transport, mbus::Reply::UP());
        EXPECT_TRUE(false); // should throw an exception
    } catch (vespalib::IllegalArgumentException &e) {
        (void)e; // expected
    }
}

void
Test::testTrace()
{
    LocalTransport transport;
    mbus::Reply::UP reply(new documentapi::RemoveDocumentReply());

    FeedToken token(transport, std::move(reply));
    token.trace(0, "foo");
    token.ack();
    reply = transport.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(!reply->hasErrors());
    std::string trace = reply->getTrace().toString();
    fprintf(stderr, "%s", trace.c_str());
    EXPECT_TRUE(trace.find("foo") != std::string::npos);
}
