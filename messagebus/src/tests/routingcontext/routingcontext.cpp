// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>


using namespace mbus;

////////////////////////////////////////////////////////////////////////////////
//
// Utilities
//
////////////////////////////////////////////////////////////////////////////////

using vespalib::make_string;

static const duration TIMEOUT = 120s;

class StringList : public std::vector<string> {
public:
    StringList &add(const string &str);
};

StringList &
StringList::add(const string &str)
{
    std::vector<string>::push_back(str); return *this;
}

class CustomPolicyFactory : public SimpleProtocol::IPolicyFactory {
private:
    friend class CustomPolicy;

    bool                     _forward;
    std::vector<string> _expectedAll;
    std::vector<string> _expectedMatched;

public:
    CustomPolicyFactory(bool forward,
                        const std::vector<string> &all,
                        const std::vector<string> &matched);
    ~CustomPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override;
};

CustomPolicyFactory::~CustomPolicyFactory() = default;

class CustomPolicy : public IRoutingPolicy {
private:
    CustomPolicyFactory &_factory;

public:
    CustomPolicy(CustomPolicyFactory &factory);
    void select(RoutingContext &ctx) override;
    void merge(RoutingContext &ctx) override;
};

CustomPolicy::CustomPolicy(CustomPolicyFactory &factory) :
    _factory(factory)
{
    // empty
}

void
CustomPolicy::select(RoutingContext &ctx)
{
    Reply::UP reply(new EmptyReply());
    reply->getTrace().setLevel(9);

    const std::vector<Route> &all = ctx.getAllRecipients();
    if (_factory._expectedAll.size() == all.size()) {
        ctx.trace(1, make_string("Got %d expected recipients.", (uint32_t)all.size()));
        for (std::vector<Route>::const_iterator it = all.begin();
             it != all.end(); ++it)
        {
            if (find(_factory._expectedAll.begin(), _factory._expectedAll.end(), it->toString()) != _factory._expectedAll.end()) {
                ctx.trace(1, make_string("Got expected recipient '%s'.", it->toString().c_str()));
            } else {
                reply->addError(Error(ErrorCode::APP_FATAL_ERROR,
                                      make_string("Matched recipient '%s' not expected.",
                                                  it->toString().c_str())));
            }
        }
    } else {
        reply->addError(Error(ErrorCode::APP_FATAL_ERROR,
                              make_string("Expected %d recipients, got %d.",
                                          (uint32_t)_factory._expectedAll.size(),
                                          (uint32_t)all.size())));
    }

    if (ctx.getNumRecipients() == all.size()) {
        for (uint32_t i = 0; i < all.size(); ++i) {
            if (all[i].toString() == ctx.getRecipient(i).toString()) {
                ctx.trace(1, make_string("getRecipient(%d) matches getAllRecipients()[%d]", i, i));
            } else {
                reply->addError(Error(ErrorCode::APP_FATAL_ERROR,
                                      make_string("getRecipient(%d) differs from getAllRecipients()[%d]", i, i)));
            }
        }
    } else {
        reply->addError(Error(ErrorCode::APP_FATAL_ERROR,
                              "getNumRecipients() differs from getAllRecipients().size()"));
    }

    std::vector<Route> matched;
    ctx.getMatchedRecipients(matched);
    if (_factory._expectedMatched.size() == matched.size()) {
        ctx.trace(1, make_string("Got %d expected recipients.", (uint32_t)matched.size()));
        for (std::vector<Route>::iterator it = matched.begin();
             it != matched.end(); ++it)
        {
            if (find(_factory._expectedMatched.begin(), _factory._expectedMatched.end(), it->toString()) != _factory._expectedMatched.end()) {
                ctx.trace(1, make_string("Got matched recipient '%s'.", it->toString().c_str()));
            } else {
                reply->addError(Error(ErrorCode::APP_FATAL_ERROR,
                                      make_string("Matched recipient '%s' not expected.",
                                                  it->toString().c_str())));
            }
        }
    } else {
        reply->addError(Error(ErrorCode::APP_FATAL_ERROR,
                              make_string("Expected %d matched recipients, got %d.",
                                          (uint32_t)_factory._expectedMatched.size(),
                                          (uint32_t)matched.size())));
    }

    if (!reply->hasErrors() && _factory._forward) {
        for (std::vector<Route>::iterator it = matched.begin();
             it != matched.end(); ++it)
        {
            ctx.addChild(*it);
        }
    } else {
        ctx.setReply(std::move(reply));
    }
}

void
CustomPolicy::merge(RoutingContext &ctx)
{
    Reply::UP ret(new EmptyReply());
    for (RoutingNodeIterator it = ctx.getChildIterator();
         it.isValid(); it.next())
    {
        const Reply &reply = it.getReplyRef();
        for (uint32_t i = 0; i < reply.getNumErrors(); ++i) {
            ret->addError(reply.getError(i));
        }
    }
    ctx.setReply(std::move(ret));
}

CustomPolicyFactory::CustomPolicyFactory(bool forward,
                                         const std::vector<string> &all,
                                         const std::vector<string> &matched) :
    _forward(forward),
    _expectedAll(all),
    _expectedMatched(matched)
{
    // empty
}

IRoutingPolicy::UP
CustomPolicyFactory::create(const string &)
{
    return IRoutingPolicy::UP(new CustomPolicy(*this));
}

Message::UP
createMessage(const string &msg)
{
    Message::UP ret(new SimpleMessage(msg));
    ret->getTrace().setLevel(9);
    return ret;
}


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
    TestData();
    ~TestData();
    bool start();
};

class Test : public vespalib::TestApp {
private:
    Message::UP createMessage(const string &msg);

public:
    int Main() override;
    void testSingleDirective(TestData &data);
    void testMoreDirectives(TestData &data);
    void testRecipientsRemain(TestData &data);
    void testConstRoute(TestData &data);
};

TEST_APPHOOK(Test);

TestData::TestData() :
    _slobrok(),
    _retryPolicy(std::make_shared<RetryTransientErrorsPolicy>()),
    _srcServer(MessageBusParams().setRetryPolicy(_retryPolicy).addProtocol(std::make_shared<SimpleProtocol>()),
               RPCNetworkParams(_slobrok.config())),
    _srcSession(),
    _srcHandler(),
    _dstServer(MessageBusParams().addProtocol(std::make_shared<SimpleProtocol>()),
               RPCNetworkParams(_slobrok.config()).setIdentity(Identity("dst"))),
    _dstSession(),
    _dstHandler()
{
    _retryPolicy->setBaseDelay(0);
}

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
    TEST_INIT("routingcontext_test");

    TestData data;
    ASSERT_TRUE(data.start());

    testSingleDirective(data);  TEST_FLUSH();
    testMoreDirectives(data);   TEST_FLUSH();
    testRecipientsRemain(data); TEST_FLUSH();
    testConstRoute(data);       TEST_FLUSH();

    TEST_DONE();
}

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

void
Test::testSingleDirective(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(
                                                                                 false,
                                                                                 StringList().add("foo").add("bar").add("baz/cox"),
                                                                                 StringList().add("foo").add("bar"))));
    data._srcServer.mb.putProtocol(protocol);
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("myroute").addHop("myhop"))
                                                           .addHop(HopSpec("myhop", "[Custom]")
                                                                   .addRecipient("foo")
                                                                   .addRecipient("bar")
                                                                   .addRecipient("baz/cox"))));
    for (uint32_t i = 0; i < 2; ++i) {
        EXPECT_TRUE(data._srcSession->send(createMessage("msg"), "myroute").isAccepted());
        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply.get() != NULL);
        printf("%s", reply->getTrace().toString().c_str());
        EXPECT_TRUE(!reply->hasErrors());
    }
}

void
Test::testMoreDirectives(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(
                                                                                 false,
                                                                                 StringList().add("foo").add("foo/bar").add("foo/bar0/baz").add("foo/bar1/baz").add("foo/bar/baz/cox"),
                                                                                 StringList().add("foo/bar0/baz").add("foo/bar1/baz"))));
    data._srcServer.mb.putProtocol(protocol);
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("myroute").addHop("myhop"))
                                                           .addHop(HopSpec("myhop", "foo/[Custom]/baz")
                                                                   .addRecipient("foo")
                                                                   .addRecipient("foo/bar")
                                                                   .addRecipient("foo/bar0/baz")
                                                                   .addRecipient("foo/bar1/baz")
                                                                   .addRecipient("foo/bar/baz/cox"))));
    for (uint32_t i = 0; i < 2; ++i) {
        EXPECT_TRUE(data._srcSession->send(createMessage("msg"), "myroute").isAccepted());
        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply.get() != NULL);
        printf("%s", reply->getTrace().toString().c_str());
        EXPECT_TRUE(!reply->hasErrors());
    }
}

void
Test::testRecipientsRemain(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("First", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(
                                                                                true,
                                                                                StringList().add("foo/bar"),
                                                                                StringList().add("foo/[Second]"))));
    simple.addPolicyFactory("Second", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(
                                                                                 false,
                                                                                 StringList().add("foo/bar"),
                                                                                 StringList().add("foo/bar"))));
    data._srcServer.mb.putProtocol(protocol);
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("myroute").addHop("myhop"))
                                                           .addHop(HopSpec("myhop", "[First]/[Second]")
                                                                   .addRecipient("foo/bar"))));
    for (uint32_t i = 0; i < 2; ++i) {
        EXPECT_TRUE(data._srcSession->send(createMessage("msg"), "myroute").isAccepted());
        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply.get() != NULL);
        printf("%s", reply->getTrace().toString().c_str());
        EXPECT_TRUE(!reply->hasErrors());
    }
}

void
Test::testConstRoute(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("DocumentRouteSelector",
                            SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(
                                                                       true,
                                                                       StringList().add("dst"),
                                                                       StringList().add("dst"))));
    data._srcServer.mb.putProtocol(protocol);
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("default").addHop("indexing"))
                                                           .addHop(HopSpec("indexing", "[DocumentRouteSelector]").addRecipient("dst"))
                                                           .addHop(HopSpec("dst", "dst/session"))));
    for (uint32_t i = 0; i < 2; ++i) {
        EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("route:default")).isAccepted());
        Message::UP msg = data._dstHandler.getMessage(TIMEOUT);
        ASSERT_TRUE(msg.get() != NULL);
        data._dstSession->acknowledge(std::move(msg));
        Reply::UP reply = data._srcHandler.getReply();
        ASSERT_TRUE(reply.get() != NULL);
        printf("%s", reply->getTrace().toString().c_str());
        EXPECT_TRUE(!reply->hasErrors());
    }
}

