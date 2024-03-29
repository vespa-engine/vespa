// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace mbus;

////////////////////////////////////////////////////////////////////////////////
//
// Setup
//
////////////////////////////////////////////////////////////////////////////////

namespace {

class TestData {
public:
    Slobrok                _slobrok;
    TestServer             _srcServer;
    SourceSession::UP      _srcSession;
    Receptor               _srcHandler;
    TestServer             _dstServer;
    DestinationSession::UP _dstSession;
    Receptor               _dstHandler;

public:
    TestData();
    ~TestData();
    bool start();
};

TestData::TestData()
    :  _slobrok(),
       _srcServer(MessageBusParams()
                  .setRetryPolicy(IRetryPolicy::SP())
                  .addProtocol(std::make_shared<SimpleProtocol>()),
                  RPCNetworkParams(_slobrok.config())),
       _srcSession(),
       _srcHandler(),
       _dstServer(MessageBusParams()
                  .addProtocol(std::make_shared<SimpleProtocol>()),
                  RPCNetworkParams(_slobrok.config())
                  .setIdentity(Identity("dst"))),
       _dstSession(),
       _dstHandler()
{
    // empty
}

TestData::~TestData() = default;

bool
TestData::start()
{
    _srcSession = _srcServer.mb.createSourceSession(SourceSessionParams()
                                                    .setThrottlePolicy(IThrottlePolicy::SP())
                                                    .setReplyHandler(_srcHandler));
    if ( ! _srcSession) {
        return false;
    }
    _dstSession = _dstServer.mb.createDestinationSession(DestinationSessionParams()
                                                         .setName("session")
                                                         .setMessageHandler(_dstHandler));
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

}

class ChokeTest : public testing::Test {
protected:
    static std::shared_ptr<TestData> _data;
    ChokeTest();
    ~ChokeTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

std::shared_ptr<TestData> ChokeTest::_data;

ChokeTest::ChokeTest() = default;
ChokeTest::~ChokeTest() = default;

void
ChokeTest::SetUpTestSuite()
{
    _data = std::make_shared<TestData>();
    ASSERT_TRUE(_data->start());
}

void
ChokeTest::TearDownTestSuite()
{
    _data.reset();
}

static const duration TIMEOUT = 120s;

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

TEST_F(ChokeTest, test_max_count)
{
    auto& data = *_data;
    uint32_t max = 10;
    data._dstServer.mb.setMaxPendingCount(max);
    std::vector<Message*> lst;
    for (uint32_t i = 0; i < max * 2; ++i) {
        if (i < max) {
            EXPECT_EQ(i, data._dstServer.mb.getPendingCount());
        } else {
            EXPECT_EQ(max, data._dstServer.mb.getPendingCount());
        }
        EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
        if (i < max) {
            Message::UP msg = data._dstHandler.getMessage(TIMEOUT);
            ASSERT_TRUE(msg);
            lst.push_back(msg.release());
        } else {
            Reply::UP reply = data._srcHandler.getReply();
            ASSERT_TRUE(reply);
            EXPECT_EQ(1u, reply->getNumErrors());
            EXPECT_EQ((uint32_t)ErrorCode::SESSION_BUSY, reply->getError(0).getCode());
        }
    }
    for (uint32_t i = 0; i < 5; ++i) {
        Message::UP msg(lst[0]);
        lst.erase(lst.begin());
        data._dstSession->acknowledge(std::move(msg));

        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply);
        EXPECT_TRUE(!reply->hasErrors());
        msg = reply->getMessage();
        ASSERT_TRUE(msg);
        EXPECT_TRUE(data._srcSession->send(std::move(msg), Route::parse("dst/session")).isAccepted());

        msg = data._dstHandler.getMessage(TIMEOUT);
        ASSERT_TRUE(msg);
        lst.push_back(msg.release());
    }
    while (!lst.empty()) {
        EXPECT_EQ(lst.size(), data._dstServer.mb.getPendingCount());
        Message::UP msg(lst[0]);
        lst.erase(lst.begin());
        data._dstSession->acknowledge(std::move(msg));

        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply);
        EXPECT_TRUE(!reply->hasErrors());
    }
    EXPECT_EQ(0u, data._dstServer.mb.getPendingCount());
}

TEST_F(ChokeTest, test_max_size)
{
    auto& data = *_data;
    uint32_t size = createMessage("msg")->getApproxSize();
    uint32_t max = size * 10;
    data._dstServer.mb.setMaxPendingSize(max);
    std::vector<Message*> lst;
    for (uint32_t i = 0; i < max * 2; i += size) {
        if (i < max) {
            EXPECT_EQ(i, data._dstServer.mb.getPendingSize());
        } else {
            EXPECT_EQ(max, data._dstServer.mb.getPendingSize());
        }
        EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
        if (i < max) {
            Message::UP msg = data._dstHandler.getMessage(TIMEOUT);
            ASSERT_TRUE(msg);
            lst.push_back(msg.release());
        } else {
            Reply::UP reply = data._srcHandler.getReply();
            ASSERT_TRUE(reply);
            EXPECT_EQ(1u, reply->getNumErrors());
            EXPECT_EQ((uint32_t)ErrorCode::SESSION_BUSY, reply->getError(0).getCode());
        }
    }
    for (uint32_t i = 0; i < 5; ++i) {
        Message::UP msg(lst[0]);
        lst.erase(lst.begin());
        data._dstSession->acknowledge(std::move(msg));

        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply);
        EXPECT_TRUE(!reply->hasErrors());
        msg = reply->getMessage();
        ASSERT_TRUE(msg);
        EXPECT_TRUE(data._srcSession->send(std::move(msg), Route::parse("dst/session")).isAccepted());

        msg = data._dstHandler.getMessage(TIMEOUT);
        ASSERT_TRUE(msg);
        lst.push_back(msg.release());
    }
    while (!lst.empty()) {
        EXPECT_EQ(size * lst.size(), data._dstServer.mb.getPendingSize());
        Message::UP msg(lst[0]);
        lst.erase(lst.begin());
        data._dstSession->acknowledge(std::move(msg));

        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply);
        EXPECT_TRUE(!reply->hasErrors());
    }
    EXPECT_EQ(0u, data._dstServer.mb.getPendingSize());
}

GTEST_MAIN_RUN_ALL_TESTS()
