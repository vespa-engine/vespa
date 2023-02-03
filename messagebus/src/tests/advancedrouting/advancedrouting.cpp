// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/testlib/custompolicy.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>

using namespace mbus;

class TestData {
public:
    Slobrok                        _slobrok;
    RetryTransientErrorsPolicy::SP _retryPolicy;
    TestServer                     _srcServer;
    SourceSession::UP              _srcSession;
    Receptor                       _srcHandler;
    TestServer                     _dstServer;
    DestinationSession::UP         _fooSession;
    Receptor                       _fooHandler;
    DestinationSession::UP         _barSession;
    Receptor                       _barHandler;
    DestinationSession::UP         _bazSession;
    Receptor                       _bazHandler;

public:
    TestData();
    ~TestData();
    bool start();
};

class Test : public vespalib::TestApp {
private:
    Message::UP createMessage(const string &msg);
    bool testTrace(const std::vector<string> &expected, const Trace &trace);

public:
    int Main() override;
    void testAdvanced(TestData &data);
};

TEST_APPHOOK(Test);

TestData::~TestData() = default;
TestData::TestData() :
    _slobrok(),
    _retryPolicy(std::make_shared<RetryTransientErrorsPolicy>()),
    _srcServer(MessageBusParams().setRetryPolicy(_retryPolicy).addProtocol(std::make_shared<SimpleProtocol>()),
               RPCNetworkParams(_slobrok.config())),
    _srcSession(),
    _srcHandler(),
    _dstServer(MessageBusParams().addProtocol(std::make_shared<SimpleProtocol>()),
               RPCNetworkParams(_slobrok.config()).setIdentity(Identity("dst"))),
    _fooSession(),
    _fooHandler(),
    _barSession(),
    _barHandler(),
    _bazSession(),
    _bazHandler()
{
    _retryPolicy->setBaseDelay(0);
}

bool
TestData::start()
{
    _srcSession = _srcServer.mb.createSourceSession(SourceSessionParams().setReplyHandler(_srcHandler));
    if ( ! _srcSession) {
        return false;
    }
    _fooSession = _dstServer.mb.createDestinationSession(DestinationSessionParams().setName("foo").setMessageHandler(_fooHandler));
    if ( ! _fooSession) {
        return false;
    }
    _barSession = _dstServer.mb.createDestinationSession(DestinationSessionParams().setName("bar").setMessageHandler(_barHandler));
    if ( ! _barSession) {
        return false;
    }
    _bazSession = _dstServer.mb.createDestinationSession(DestinationSessionParams().setName("baz").setMessageHandler(_bazHandler));
    if ( ! _bazSession) {
        return false;
    }
    if (!_srcServer.waitSlobrok("dst/*", 3u)) {
        return false;
    }
    return true;
}

Message::UP
Test::createMessage(const string &msg)
{
    auto ret = std::make_unique<SimpleMessage>(msg);
    ret->getTrace().setLevel(9);
    return ret;
}

int
Test::Main()
{
    TEST_INIT("routing_test");

    TestData data;
    ASSERT_TRUE(data.start());

    testAdvanced(data); TEST_FLUSH();

    TEST_DONE();
}

void
Test::testAdvanced(TestData &data)
{
    const duration TIMEOUT = 60s;
    IProtocol::SP protocol(new SimpleProtocol());
    auto &simple = dynamic_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(false, ErrorCode::NO_ADDRESS_FOR_SERVICE)));
    data._srcServer.mb.putProtocol(protocol);
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("bar", "dst/bar"))
                                                           .addHop(HopSpec("baz", "dst/baz"))
                                                           .addRoute(RouteSpec("baz").addHop("baz"))));
    string route = vespalib::make_string("[Custom:%s,bar,route:baz,dst/cox,?dst/unknown]",
                                              data._fooSession->getConnectionSpec().c_str());
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse(route)).isAccepted());

    // Initial send.
    Message::UP msg = data._fooHandler.getMessage(TIMEOUT);
    ASSERT_TRUE(msg);
    data._fooSession->acknowledge(std::move(msg));
    msg = data._barHandler.getMessage(TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::TRANSIENT_ERROR, "bar"));
    data._barSession->reply(std::move(reply));
    msg = data._bazHandler.getMessage(TIMEOUT);
    ASSERT_TRUE(msg);
    reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::TRANSIENT_ERROR, "baz1"));
    data._bazSession->reply(std::move(reply));

    // First retry.
    msg = data._fooHandler.getMessageNow();
    ASSERT_FALSE(msg);
    msg = data._barHandler.getMessage(TIMEOUT);
    ASSERT_TRUE(msg);
    data._barSession->acknowledge(std::move(msg));
    msg = data._bazHandler.getMessage(TIMEOUT);
    ASSERT_TRUE(msg);
    reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::TRANSIENT_ERROR, "baz2"));
    data._bazSession->reply(std::move(reply));

    // Second retry.
    msg = data._fooHandler.getMessageNow();
    ASSERT_FALSE(msg);
    msg = data._barHandler.getMessageNow();
    ASSERT_FALSE(msg);
    msg = data._bazHandler.getMessage(TIMEOUT);
    ASSERT_TRUE(msg);
    reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::FATAL_ERROR, "baz3"));
    data._bazSession->reply(std::move(reply));

    // Done.
    reply = data._srcHandler.getReply();
    ASSERT_TRUE(reply);
    printf("%s", reply->getTrace().toString().c_str());
    EXPECT_EQUAL(2u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::FATAL_ERROR, reply->getError(0).getCode());
    EXPECT_EQUAL((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(1).getCode());
}
