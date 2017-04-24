// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("routing_test");

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/routing/errordirective.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/messagebus/testlib/custompolicy.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

class StringList : public std::vector<string> {
public:
    StringList &add(const string &str);
};

StringList &
StringList::add(const string &str)
{
    std::vector<string>::push_back(str); return *this;
}

static const double GET_MESSAGE_TIMEOUT = 60.0;

////////////////////////////////////////////////////////////////////////////////
//
// Setup
//
////////////////////////////////////////////////////////////////////////////////

class TestData {
public:
    Slobrok                        _slobrok;
    RetryTransientErrorsPolicy::SP _retryPolicy;
    TestServer                     _srcServer;
    SourceSession::UP              _srcSession;
    Receptor                       _srcHandler;
    TestServer                     _dstServer;
    DestinationSession::UP         _dstSession;
    Receptor                       _dstHandler;

public:
    ~TestData();
    TestData();
    bool start();
};

class Test : public vespalib::TestApp {
private:
    Message::UP createMessage(const string &msg);
    void replyFromDestination(TestData &data, Message::UP msg, uint32_t errorCode, double retryDelay);

public:
    int Main() override;
    void testRetryTag(TestData &data);
    void testRetryEnabledTag(TestData &data);
    void testTransientError(TestData &data);
    void testFatalError(TestData &data);
    void testDisableRetry(TestData &data);
    void testRetryDelay(TestData &data);
    void testRequestRetryDelay(TestData &data);
};

TEST_APPHOOK(Test);

TestData::TestData() :
    _slobrok(),
    _retryPolicy(new RetryTransientErrorsPolicy()),
    _srcServer(MessageBusParams().setRetryPolicy(_retryPolicy).addProtocol(IProtocol::SP(new SimpleProtocol())),
               RPCNetworkParams().setSlobrokConfig(_slobrok.config())),
    _srcSession(),
    _srcHandler(),
    _dstServer(MessageBusParams().addProtocol(IProtocol::SP(new SimpleProtocol())),
               RPCNetworkParams().setIdentity(Identity("dst")).setSlobrokConfig(_slobrok.config())),
    _dstSession(),
    _dstHandler()
{ }

TestData::~TestData() {}

bool
TestData::start()
{
    _srcSession = _srcServer.mb.createSourceSession(SourceSessionParams().setReplyHandler(_srcHandler));
    if (_srcSession.get() == NULL) {
        return false;
    }
    _dstSession = _dstServer.mb.createDestinationSession(DestinationSessionParams().setName("session").setMessageHandler(_dstHandler));
    if (_dstSession.get() == NULL) {
        return false;
    }
    if (!_srcServer.waitSlobrok("dst/session", 1u)) {
        return false;
    }
    return true;
}

Message::UP
Test::createMessage(const string &msg)
{
    Message::UP ret(new SimpleMessage(msg));
    ret->getTrace().setLevel(9);
    return ret;
}

int
Test::Main()
{
    TEST_INIT("resender_test");

    TestData data;
    ASSERT_TRUE(data.start());

    testRetryTag(data);          TEST_FLUSH();
    testRetryEnabledTag(data);   TEST_FLUSH();
    testTransientError(data);    TEST_FLUSH();
    testFatalError(data);        TEST_FLUSH();
    testDisableRetry(data);      TEST_FLUSH();
    testRetryDelay(data);        TEST_FLUSH();
    testRequestRetryDelay(data); TEST_FLUSH();

    TEST_DONE();
}

void
Test::replyFromDestination(TestData &data, Message::UP msg, uint32_t errorCode, double retryDelay)
{
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    if (errorCode != ErrorCode::NONE) {
        reply->addError(Error(errorCode, "err"));
    }
    reply->setRetryDelay(retryDelay);
    data._dstSession->reply(std::move(reply));
}

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

void
Test::testRetryTag(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_EQUAL(i, msg->getRetry());
        EXPECT_EQUAL(true, msg->getRetryEnabled());
        replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
        msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
        ASSERT_TRUE(msg.get() != NULL);
    }
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(!reply->hasErrors());
    msg = data._dstHandler.getMessage(0);
    EXPECT_TRUE(msg.get() == NULL);
    printf("%s", reply->getTrace().toString().c_str());
}

void
Test::testRetryEnabledTag(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    Message::UP msg = createMessage("msg");
    msg->setRetryEnabled(false);
    EXPECT_TRUE(data._srcSession->send(std::move(msg), Route::parse("dst/session")).isAccepted());
    msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    EXPECT_EQUAL(false, msg->getRetryEnabled());
    replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(reply->hasErrors());
    msg = data._dstHandler.getMessage(0);
    EXPECT_TRUE(msg.get() == NULL);
    printf("%s", reply->getTrace().toString().c_str());
}

void
Test::testTransientError(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
    msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessage(0);
    EXPECT_TRUE(msg.get() == NULL);
    printf("%s", reply->getTrace().toString().c_str());
}

void
Test::testFatalError(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessage(0);
    EXPECT_TRUE(msg.get() == NULL);
    printf("%s", reply->getTrace().toString().c_str());
}

void
Test::testDisableRetry(TestData &data)
{
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(reply->hasErrors());
    EXPECT_TRUE(!reply->hasFatalErrors());
    msg = data._dstHandler.getMessage(0);
    EXPECT_TRUE(msg.get() == NULL);
    printf("%s", reply->getTrace().toString().c_str());
}

void
Test::testRetryDelay(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    data._retryPolicy->setBaseDelay(0.01);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_EQUAL(i, msg->getRetry());
        replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, -1);
        msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
        ASSERT_TRUE(msg.get() != NULL);
    }
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessage(0);
    EXPECT_TRUE(msg.get() == NULL);

    string trace = reply->getTrace().toString();
    printf("%s", trace.c_str());
    EXPECT_TRUE(trace.find("retry 1 in 0.01") != string::npos);
    EXPECT_TRUE(trace.find("retry 2 in 0.02") != string::npos);
    EXPECT_TRUE(trace.find("retry 3 in 0.03") != string::npos);
    EXPECT_TRUE(trace.find("retry 4 in 0.04") != string::npos);
    EXPECT_TRUE(trace.find("retry 5 in 0.05") != string::npos);
}

void
Test::testRequestRetryDelay(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    data._retryPolicy->setBaseDelay(1);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg.get() != NULL);
    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_EQUAL(i, msg->getRetry());
        replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, i / 50.0);
        msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
        ASSERT_TRUE(msg.get() != NULL);
    }
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply.get() != NULL);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessage(0);
    EXPECT_TRUE(msg.get() == NULL);

    string trace = reply->getTrace().toString();
    printf("%s", trace.c_str());
    EXPECT_TRUE(trace.find("retry 1 in 0") != string::npos);
    EXPECT_TRUE(trace.find("retry 2 in 0.02") != string::npos);
    EXPECT_TRUE(trace.find("retry 3 in 0.04") != string::npos);
    EXPECT_TRUE(trace.find("retry 4 in 0.06") != string::npos);
    EXPECT_TRUE(trace.find("retry 5 in 0.08") != string::npos);
}

