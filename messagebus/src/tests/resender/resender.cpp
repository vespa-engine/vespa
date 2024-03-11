// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace mbus;

namespace {

static const duration GET_MESSAGE_TIMEOUT = 60s;

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

TestData::TestData()
    : _slobrok(),
      _retryPolicy(new RetryTransientErrorsPolicy()),
      _srcServer(MessageBusParams().setRetryPolicy(_retryPolicy).addProtocol(std::make_shared<SimpleProtocol>()),
                 RPCNetworkParams(_slobrok.config())),
      _srcSession(),
      _srcHandler(),
      _dstServer(MessageBusParams().addProtocol(std::make_shared<SimpleProtocol>()),
                 RPCNetworkParams(_slobrok.config()).setIdentity(Identity("dst"))),
      _dstSession(),
      _dstHandler()
{ }

TestData::~TestData() = default;

bool
TestData::start()
{
    _srcSession = _srcServer.mb.createSourceSession(SourceSessionParams().setReplyHandler(_srcHandler));
    if ( ! _srcSession) {
        return false;
    }
    _dstSession = _dstServer.mb.createDestinationSession(DestinationSessionParams().setName("session").setMessageHandler(_dstHandler));
    if ( ! _dstSession) {
        return false;
    }
    if (!_srcServer.waitSlobrok("dst/session", 1u)) {
        return false;
    }
    return true;
}

std::unique_ptr<Message>
createMessage(const string &msg)
{
    Message::UP ret(new SimpleMessage(msg));
    ret->getTrace().setLevel(9);
    return ret;
}

void
replyFromDestination(TestData &data, Message::UP msg, uint32_t errorCode, double retryDelay)
{
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    if (errorCode != ErrorCode::NONE) {
        reply->addError(Error(errorCode, "err"));
    }
    reply->setRetryDelay(retryDelay);
    data._dstSession->reply(std::move(reply));
}

}

class ResenderTest : public testing::Test {
protected:
    static std::shared_ptr<TestData> _data;
    ResenderTest();
    ~ResenderTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

std::shared_ptr<TestData> ResenderTest::_data;

ResenderTest::ResenderTest() = default;
ResenderTest::~ResenderTest() = default;

void
ResenderTest::SetUpTestSuite()
{
    _data = std::make_shared<TestData>();
    ASSERT_TRUE(_data->start());
}

void
ResenderTest::TearDownTestSuite()
{
    _data.reset();
}

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

TEST_F(ResenderTest, test_retry_tag)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_EQ(i, msg->getRetry());
        EXPECT_EQ(true, msg->getRetryEnabled());
        replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
        msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
        ASSERT_TRUE(msg);
    }
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    msg = data._dstHandler.getMessageNow();
    EXPECT_FALSE(msg);
    printf("%s", reply->getTrace().toString().c_str());
}

TEST_F(ResenderTest, test_retry_enabled_tag)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(true);
    Message::UP msg = createMessage("msg");
    msg->setRetryEnabled(false);
    EXPECT_TRUE(data._srcSession->send(std::move(msg), Route::parse("dst/session")).isAccepted());
    msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    EXPECT_EQ(false, msg->getRetryEnabled());
    replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->hasErrors());
    msg = data._dstHandler.getMessageNow();
    EXPECT_FALSE(msg);
    printf("%s", reply->getTrace().toString().c_str());
}

TEST_F(ResenderTest, test_transient_error)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
    msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessageNow();
    EXPECT_FALSE(msg);
    printf("%s", reply->getTrace().toString().c_str());
}

TEST_F(ResenderTest, test_fatal_error)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessageNow();
    EXPECT_FALSE(msg);
    printf("%s", reply->getTrace().toString().c_str());
}

TEST_F(ResenderTest, test_disable_retry)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->hasErrors());
    EXPECT_TRUE(!reply->hasFatalErrors());
    msg = data._dstHandler.getMessageNow();
    EXPECT_FALSE(msg);
    printf("%s", reply->getTrace().toString().c_str());
}

TEST_F(ResenderTest, test_retry_delay)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(true);
    data._retryPolicy->setBaseDelay(0.01);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_EQ(i, msg->getRetry());
        replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, -1);
        msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
        ASSERT_TRUE(msg);
    }
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessageNow();
    EXPECT_FALSE(msg);

    string trace = reply->getTrace().toString();
    EXPECT_TRUE(trace.find("retry 1 in 0.000") != string::npos);
    EXPECT_TRUE(trace.find("retry 2 in 0.020") != string::npos);
    EXPECT_TRUE(trace.find("retry 3 in 0.040") != string::npos);
    EXPECT_TRUE(trace.find("retry 4 in 0.080") != string::npos);
    EXPECT_TRUE(trace.find("retry 5 in 0.160") != string::npos);
}

TEST_F(ResenderTest, test_request_retry_delay)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(true);
    data._retryPolicy->setBaseDelay(1);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
    ASSERT_TRUE(msg);
    for (uint32_t i = 0; i < 5; ++i) {
        EXPECT_EQ(i, msg->getRetry());
        replyFromDestination(data, std::move(msg), ErrorCode::APP_TRANSIENT_ERROR, i / 50.0);
        msg = data._dstHandler.getMessage(GET_MESSAGE_TIMEOUT);
        ASSERT_TRUE(msg);
    }
    replyFromDestination(data, std::move(msg), ErrorCode::APP_FATAL_ERROR, 0);
    Reply::UP reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    EXPECT_TRUE(reply->hasFatalErrors());
    msg = data._dstHandler.getMessageNow();
    EXPECT_FALSE(msg);

    string trace = reply->getTrace().toString();
    EXPECT_TRUE(trace.find("retry 1 in 0.000") != string::npos);
    EXPECT_TRUE(trace.find("retry 2 in 0.020") != string::npos);
    EXPECT_TRUE(trace.find("retry 3 in 0.040") != string::npos);
    EXPECT_TRUE(trace.find("retry 4 in 0.060") != string::npos);
    EXPECT_TRUE(trace.find("retry 5 in 0.080") != string::npos);
}

GTEST_MAIN_RUN_ALL_TESTS()
