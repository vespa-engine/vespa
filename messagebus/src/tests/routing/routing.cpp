// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/routing/errordirective.h>
#include <vespa/messagebus/routing/retrytransienterrorspolicy.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <vespa/messagebus/testlib/custompolicy.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/component/vtag.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("routing_test");

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
    std::vector<string>::push_back(str);
    return *this;
}

class UIntList : public std::vector<uint32_t> {
public:
    UIntList &add(uint32_t i);
};

UIntList &
UIntList::add(uint32_t i)
{
    std::vector<uint32_t>::push_back(i);
    return *this;
}

class RemoveReplyPolicy : public CustomPolicy {
private:
    uint32_t _idxRemove;
public:
    RemoveReplyPolicy(bool selectOnRetry,
                      const std::vector<uint32_t> consumableErrors,
                      const std::vector<Route> routes,
                      uint32_t idxRemove);
    void merge(RoutingContext &ctx) override;
};

RemoveReplyPolicy::RemoveReplyPolicy(bool selectOnRetry,
                                     const std::vector<uint32_t> consumableErrors,
                                     const std::vector<Route> routes,
                                     uint32_t idxRemove) :
    CustomPolicy::CustomPolicy(selectOnRetry, consumableErrors, routes),
    _idxRemove(idxRemove)
{
    // empty
}

void
RemoveReplyPolicy::merge(RoutingContext &ctx)
{
    ctx.setReply(ctx.getChildIterator().skip(_idxRemove).removeReply());
}

class RemoveReplyPolicyFactory : public SimpleProtocol::IPolicyFactory {
private:
    bool                  _selectOnRetry;
    std::vector<uint32_t> _consumableErrors;
    uint32_t              _idxRemove;
public:
    RemoveReplyPolicyFactory(bool selectOnRetry,
                             const std::vector<uint32_t> &consumableErrors,
                             uint32_t idxRemove);
    ~RemoveReplyPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override;
};

RemoveReplyPolicyFactory::~RemoveReplyPolicyFactory() = default;

RemoveReplyPolicyFactory::RemoveReplyPolicyFactory(bool selectOnRetry,
                                                   const std::vector<uint32_t> &consumableErrors,
                                                   uint32_t idxRemove) :
    _selectOnRetry(selectOnRetry),
    _consumableErrors(consumableErrors),
    _idxRemove(idxRemove)
{
    // empty
}

IRoutingPolicy::UP
RemoveReplyPolicyFactory::create(const string &param)
{
    std::vector<Route> routes;
    CustomPolicyFactory::parseRoutes(param, routes);
    return IRoutingPolicy::UP(new RemoveReplyPolicy(_selectOnRetry, _consumableErrors, routes, _idxRemove));
}

class ReuseReplyPolicy : public CustomPolicy {
private:
    std::vector<uint32_t> _errorMask;
public:
    ReuseReplyPolicy(bool selectOnRetry,
                     const std::vector<uint32_t> &errorMask,
                     const std::vector<Route> &routes);
    void merge(RoutingContext &ctx) override;
};

ReuseReplyPolicy::ReuseReplyPolicy(bool selectOnRetry,
                                   const std::vector<uint32_t> &errorMask,
                                   const std::vector<Route> &routes) :
    CustomPolicy::CustomPolicy(selectOnRetry, errorMask, routes),
    _errorMask(errorMask)
{
    // empty
}

void
ReuseReplyPolicy::merge(RoutingContext &ctx)
{
    Reply::UP ret(new EmptyReply());
    uint32_t idx = 0;
    int idxFirstOk = -1;
    for (RoutingNodeIterator it = ctx.getChildIterator();
         it.isValid(); it.next(), ++idx)
    {
        const Reply &ref = it.getReplyRef();
        if (!ref.hasErrors()) {
            if (idxFirstOk < 0) {
                idxFirstOk = idx;
            }
        } else {
            for (uint32_t i = 0; i < ref.getNumErrors(); ++i) {
                Error err = ref.getError(i);
                if (find(_errorMask.begin(), _errorMask.end(), err.getCode()) == _errorMask.end()) {
                    ret->addError(err);
                }
            }
        }
    }
    if (ret->hasErrors()) {
        ctx.setReply(std::move(ret));
    } else {
        ctx.setReply(ctx.getChildIterator().skip(idxFirstOk).removeReply());
    }
}

class ReuseReplyPolicyFactory : public SimpleProtocol::IPolicyFactory {
private:
    bool                  _selectOnRetry;
    std::vector<uint32_t> _errorMask;
public:
    ReuseReplyPolicyFactory(bool selectOnRetry,
                            const std::vector<uint32_t> &errorMask);
    ~ReuseReplyPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override;
};

ReuseReplyPolicyFactory::ReuseReplyPolicyFactory(bool selectOnRetry,
                                                 const std::vector<uint32_t> &errorMask) :
    _selectOnRetry(selectOnRetry),
    _errorMask(errorMask)
{
    // empty
}

ReuseReplyPolicyFactory::~ReuseReplyPolicyFactory() = default;

IRoutingPolicy::UP
ReuseReplyPolicyFactory::create(const string &param)
{
    std::vector<Route> routes;
    CustomPolicyFactory::parseRoutes(param, routes);
    return IRoutingPolicy::UP(new ReuseReplyPolicy(_selectOnRetry, _errorMask, routes));
}

class SetReplyPolicy : public IRoutingPolicy {
private:
    bool                  _selectOnRetry;
    std::vector<uint32_t> _errors;
    string           _param;
    uint32_t              _idx;
public:
    SetReplyPolicy(bool selectOnRetry,
                   const std::vector<uint32_t> &errors,
                   const string &param);
    void select(RoutingContext &ctx) override;
    void merge(RoutingContext &ctx) override;
};

SetReplyPolicy::SetReplyPolicy(bool selectOnRetry,
                               const std::vector<uint32_t> &errors,
                               const string &param) :
    _selectOnRetry(selectOnRetry),
    _errors(errors),
    _param(param),
    _idx(0)
{
    // empty
}

void
SetReplyPolicy::select(RoutingContext &ctx)
{
    uint32_t idx = _idx++;
    uint32_t err = _errors[idx < _errors.size() ? idx : _errors.size() - 1];
    if (err != ErrorCode::NONE) {
        ctx.setError(err, _param);
    } else {
        ctx.setReply(Reply::UP(new EmptyReply()));
    }
    ctx.setSelectOnRetry(_selectOnRetry);
}

void
SetReplyPolicy::merge(RoutingContext &ctx)
{
    Reply::UP reply(new EmptyReply());
    reply->addError(Error(ErrorCode::FATAL_ERROR, "Merge should not be called when select() sets a reply."));
    ctx.setReply(std::move(reply));
}

class SetReplyPolicyFactory : public SimpleProtocol::IPolicyFactory {
private:
    bool                  _selectOnRetry;
    std::vector<uint32_t> _errors;
public:
    SetReplyPolicyFactory(bool selectOnRetry,
                          const std::vector<uint32_t> &errors);
    ~SetReplyPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override;
};

SetReplyPolicyFactory::SetReplyPolicyFactory(bool selectOnRetry,
                                             const std::vector<uint32_t> &errors) :
    _selectOnRetry(selectOnRetry),
    _errors(errors)
{
    // empty
}

SetReplyPolicyFactory::~SetReplyPolicyFactory() = default;

IRoutingPolicy::UP
SetReplyPolicyFactory::create(const string &param)
{
    return IRoutingPolicy::UP(new SetReplyPolicy(_selectOnRetry, _errors, param));
}

class TestException : public std::exception {
    virtual const char* what() const throw() override {
        return "{test exception}";
    }
};

class SelectExceptionPolicy : public IRoutingPolicy {
public:
    void select(RoutingContext &ctx) override {
        (void)ctx;
        throw TestException();
    }

    void merge(RoutingContext &ctx) override {
        (void)ctx;
    }
};

class SelectExceptionPolicyFactory : public SimpleProtocol::IPolicyFactory {
public:
    ~SelectExceptionPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override {
        (void)param;
        return IRoutingPolicy::UP(new SelectExceptionPolicy());
    }
};

SelectExceptionPolicyFactory::~SelectExceptionPolicyFactory() = default;

class MergeExceptionPolicy : public IRoutingPolicy {
private:
    const string _select;

public:
    MergeExceptionPolicy(const string &param)
        : _select(param)
    {
        // empty
    }

    void select(RoutingContext &ctx) override {
        ctx.addChild(Route::parse(_select));
    }

    void merge(RoutingContext &ctx) override {
        (void)ctx;
        throw TestException();
    }
};

class MergeExceptionPolicyFactory : public SimpleProtocol::IPolicyFactory {
public:
    ~MergeExceptionPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override {
        return IRoutingPolicy::UP(new MergeExceptionPolicy(param));
    }
};

MergeExceptionPolicyFactory::~MergeExceptionPolicyFactory() = default;

class MyPolicyFactory : public SimpleProtocol::IPolicyFactory {
private:
    string   _selectRoute;
    uint32_t _selectError;
    bool     _selectException;
    bool     _mergeFromChild;
    uint32_t _mergeError;
    bool     _mergeException;

public:
    friend class MyPolicy;

    MyPolicyFactory(const string &selectRoute,
                    uint32_t &selectError,
                    bool selectException,
                    bool mergeFromChild,
                    uint32_t mergeError,
                    bool mergeException) :
        _selectRoute(selectRoute),
        _selectError(selectError),
        _selectException(selectException),
        _mergeFromChild(mergeFromChild),
        _mergeError(mergeError),
        _mergeException(mergeException)
    {
        // empty
    }

    IRoutingPolicy::UP 
    create(const string &param) override;

    static MyPolicyFactory::SP
    newInstance(const string &selectRoute,
                uint32_t selectError,
                bool selectException,
                bool mergeFromChild,
                uint32_t mergeError,
                bool mergeException) 
    {
        MyPolicyFactory::SP ptr;
        ptr.reset(new MyPolicyFactory(selectRoute, selectError, selectException,
                                      mergeFromChild, mergeError, mergeException));
        return ptr;
    }

    static MyPolicyFactory::SP 
    newSelectAndMerge(const string &select) 
    {
        return newInstance(select, ErrorCode::NONE, false, true, ErrorCode::NONE, false);
    }

    static MyPolicyFactory::SP 
    newEmptySelection() 
    {
        return newInstance("", ErrorCode::NONE, false, false, ErrorCode::NONE, false);
    }

    static MyPolicyFactory::SP 
    newSelectError(uint32_t errCode)
    {
        return newInstance("", errCode, false, false, ErrorCode::NONE, false);
    }

    static MyPolicyFactory::SP 
    newSelectException() 
    {
        return newInstance("", ErrorCode::NONE, true, false, ErrorCode::NONE, false);
    }

    static MyPolicyFactory::SP 
    newSelectAndThrow(const string &select) 
    {
        return newInstance(select, ErrorCode::NONE, true, false, ErrorCode::NONE, false);
    }

    static MyPolicyFactory::SP 
    newEmptyMerge(const string &select) 
    {
        return newInstance(select, ErrorCode::NONE, false, false, ErrorCode::NONE, false);
    }

    static MyPolicyFactory::SP 
    newMergeError(const string &select, int errCode) 
    {
        return newInstance(select, ErrorCode::NONE, false, false, errCode, false);
    }

    static MyPolicyFactory::SP 
    newMergeException(const string &select) 
    {
        return newInstance(select, ErrorCode::NONE, false, false, ErrorCode::NONE, true);
    }

    static MyPolicyFactory::SP 
    newMergeAndThrow(const string &select)
    {
        return newInstance(select, ErrorCode::NONE, false, true, ErrorCode::NONE, true);
    }    
};

class MyPolicy : public IRoutingPolicy {
private:
    const MyPolicyFactory &_parent;
    
public:
    MyPolicy(const MyPolicyFactory &parent) :
        _parent(parent)
    {}

    void select(RoutingContext &ctx) override
    {
        if (!_parent._selectRoute.empty()) {
            ctx.addChild(Route::parse(_parent._selectRoute));
        }
        if (_parent._selectError != ErrorCode::NONE) {
            Reply::UP reply(new EmptyReply());
            reply->addError(Error(_parent._selectError, "err"));
            ctx.setReply(std::move(reply));
        }
        if (_parent._selectException) {
            throw TestException();
        }
    }

    void merge(RoutingContext &ctx) override
    {
        if (_parent._mergeError != ErrorCode::NONE) {
            Reply::UP reply(new EmptyReply());
            reply->addError(Error(_parent._mergeError, "err"));
            ctx.setReply(std::move(reply));
        } else if (_parent._mergeFromChild) {
            ctx.setReply(ctx.getChildIterator().removeReply());
        }
        if (_parent._mergeException) {
            throw TestException();
        }
    }
};

IRoutingPolicy::UP
MyPolicyFactory::create(const string &param)
{
    (void)param;
    return IRoutingPolicy::UP(new MyPolicy(*this));
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
    Message::UP createMessage(const string &msg, uint32_t level = 9);
    void setupRouting(TestData &data, const RoutingTableSpec &spec);
    void setupPolicy(TestData &data, const string &policyName, 
                     SimpleProtocol::IPolicyFactory::SP policy);
    bool testAcknowledge(TestData &data);
    bool testSend(TestData &data, const string &route, uint32_t level = 9);
    bool testTrace(TestData &data, const std::vector<string> &expected);
    bool testTrace(const std::vector<string> &expected, const Trace &trace);

    static const duration RECEPTOR_TIMEOUT;

public:
    int Main() override;
    void testNoRoutingTable(TestData &data);
    void testUnknownRoute(TestData &data);
    void testNoRoute(TestData &data);
    void testRecognizeHopName(TestData &data);
    void testRecognizeRouteDirective(TestData &data);
    void testRecognizeRouteName(TestData &data);
    void testHopResolutionOverflow(TestData &data);
    void testRouteResolutionOverflow(TestData &data);
    void testInsertRoute(TestData &data);
    void testErrorDirective(TestData &data);
    void testSelectError(TestData &data);
    void testSelectNone(TestData &data);
    void testSelectOne(TestData &data);
    void testResend1(TestData &data);
    void testResend2(TestData &data);
    void testNoResend(TestData &data);
    void testSelectOnResend(TestData &data);
    void testNoSelectOnResend(TestData &data);
    void testCanConsumeError(TestData &data);
    void testCantConsumeError(TestData &data);
    void testNestedPolicies(TestData &data);
    void testRemoveReply(TestData &data);
    void testSetReply(TestData &data);
    void testResendSetAndReuseReply(TestData &data);
    void testResendSetAndRemoveReply(TestData &data);
    void testHopIgnoresReply(TestData &data);
    void testHopBlueprintIgnoresReply(TestData &data);
    void testAcceptEmptyRoute(TestData &data);
    void testAbortOnlyActiveNodes(TestData &data);
    void testTimeout(TestData &data);
    void testUnknownPolicy(TestData &data);
    void testSelectException(TestData &data);
    void testMergeException(TestData &data);

    void requireThatIgnoreFlagPersistsThroughHopLookup(TestData &data);
    void requireThatIgnoreFlagPersistsThroughRouteLookup(TestData &data);
    void requireThatIgnoreFlagPersistsThroughPolicySelect(TestData &data);
    void requireThatIgnoreFlagIsSerializedWithMessage(TestData &data);
    void requireThatIgnoreFlagDoesNotInterfere(TestData &data);
    void requireThatEmptySelectionCanBeIgnored(TestData &data);
    void requireThatSelectErrorCanBeIgnored(TestData &data);
    void requireThatSelectExceptionCanBeIgnored(TestData &data);
    void requireThatSelectAndThrowCanBeIgnored(TestData &data);
    void requireThatEmptyMergeCanBeIgnored(TestData &data);
    void requireThatMergeErrorCanBeIgnored(TestData &data);
    void requireThatMergeExceptionCanBeIgnored(TestData &data);
    void requireThatMergeAndThrowCanBeIgnored(TestData &data);
    void requireThatAllocServiceCanBeIgnored(TestData &data);
    void requireThatDepthLimitCanBeIgnored(TestData &data);
};

const duration Test::RECEPTOR_TIMEOUT = 120s;

TEST_APPHOOK(Test);

TestData::TestData() :
    _slobrok(),
    _retryPolicy(std::make_shared<RetryTransientErrorsPolicy>()),
    _srcServer(MessageBusParams()
               .setRetryPolicy(_retryPolicy)
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
    _retryPolicy->setBaseDelay(0);
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

Message::UP
Test::createMessage(const string &msg, uint32_t level)
{
    Message::UP ret(new SimpleMessage(msg));
    ret->getTrace().setLevel(level);
    return ret;
}

void 
Test::setupRouting(TestData &data, const RoutingTableSpec &spec)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(spec));
}

void 
Test::setupPolicy(TestData &data, const string &policyName,
                  SimpleProtocol::IPolicyFactory::SP policy)
{
    IProtocol::SP ptr(new SimpleProtocol());
    static_cast<SimpleProtocol&>(*ptr).addPolicyFactory(policyName, policy);
    data._srcServer.mb.putProtocol(ptr);
}

bool 
Test::testAcknowledge(TestData &data)
{
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    if (!EXPECT_TRUE(msg)) {
        return false;
    }
    data._dstSession->acknowledge(std::move(msg));
    return true;
}
 
bool 
Test::testSend(TestData &data, const string &route, uint32_t level) 
{
    Message::UP msg = createMessage("msg", level);
    msg->setRoute(Route::parse(route));
    return data._srcSession->send(std::move(msg)).isAccepted();
}

bool 
Test::testTrace(TestData &data, const std::vector<string> &expected)
{
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    if (!EXPECT_TRUE(reply)) {
        return false;
    }
    if (!EXPECT_TRUE(!reply->hasErrors())) {
        return false;
    }
    return testTrace(expected, reply->getTrace());
}

bool
Test::testTrace(const std::vector<string> &expected, const Trace &trace)
{
    string version = vespalib::Vtag::currentVersion.toString();
    string actual = trace.toString();
    size_t pos = 0;
    for (uint32_t i = 0; i < expected.size(); ++i) {
        string line = expected[i];
        size_t versionIdx = line.find("${VERSION}");
        if (versionIdx != string::npos) {
            line = line.replace(versionIdx, 10, version);
        }
        if (line[0] == '-') {
            string str = line.substr(1);
            if (!EXPECT_TRUE(actual.find(str, pos) == string::npos)) {
                LOG(error, "Line %d '%s' not expected.", i, str.c_str());
                return false;
            }
        } else {
            pos = actual.find(line, pos);
            if (!EXPECT_TRUE(pos != string::npos)) {
                LOG(error, "Line %d '%s' missing.", i, line.c_str());
                return false;
            }
            ++pos;
        }
    }
    return true;
}

int
Test::Main()
{
    TEST_INIT("routing_test");

    TestData data;
    ASSERT_TRUE(data.start());

    testNoRoutingTable(data);            TEST_FLUSH();
    testUnknownRoute(data);              TEST_FLUSH();
    testNoRoute(data);                   TEST_FLUSH();
    testRecognizeHopName(data);          TEST_FLUSH();
    testRecognizeRouteDirective(data);   TEST_FLUSH();
    testRecognizeRouteName(data);        TEST_FLUSH();
    testHopResolutionOverflow(data);     TEST_FLUSH();
    testRouteResolutionOverflow(data);   TEST_FLUSH();
    testInsertRoute(data);               TEST_FLUSH();
    testErrorDirective(data);            TEST_FLUSH();
    testSelectError(data);               TEST_FLUSH();
    testSelectNone(data);                TEST_FLUSH();
    testSelectOne(data);                 TEST_FLUSH();
    testResend1(data);                   TEST_FLUSH();
    testResend2(data);                   TEST_FLUSH();
    testNoResend(data);                  TEST_FLUSH();
    testSelectOnResend(data);            TEST_FLUSH();
    testNoSelectOnResend(data);          TEST_FLUSH();
    testCanConsumeError(data);           TEST_FLUSH();
    testCantConsumeError(data);          TEST_FLUSH();
    testNestedPolicies(data);            TEST_FLUSH();
    testRemoveReply(data);               TEST_FLUSH();
    testSetReply(data);                  TEST_FLUSH();
    testResendSetAndReuseReply(data);    TEST_FLUSH();
    testResendSetAndRemoveReply(data);   TEST_FLUSH();
    testHopIgnoresReply(data);           TEST_FLUSH();
    testHopBlueprintIgnoresReply(data);  TEST_FLUSH();
    testAcceptEmptyRoute(data);          TEST_FLUSH();
    testAbortOnlyActiveNodes(data);      TEST_FLUSH();
    testUnknownPolicy(data);             TEST_FLUSH();
    testSelectException(data);           TEST_FLUSH();
    testMergeException(data);            TEST_FLUSH();

    requireThatIgnoreFlagPersistsThroughHopLookup(data);    TEST_FLUSH();
    requireThatIgnoreFlagPersistsThroughRouteLookup(data);  TEST_FLUSH();
    requireThatIgnoreFlagPersistsThroughPolicySelect(data); TEST_FLUSH();
    requireThatIgnoreFlagIsSerializedWithMessage(data);     TEST_FLUSH();
    requireThatIgnoreFlagDoesNotInterfere(data);            TEST_FLUSH();
    requireThatEmptySelectionCanBeIgnored(data);            TEST_FLUSH();
    requireThatSelectErrorCanBeIgnored(data);               TEST_FLUSH();
    requireThatSelectExceptionCanBeIgnored(data);           TEST_FLUSH();
    requireThatSelectAndThrowCanBeIgnored(data);            TEST_FLUSH();
    requireThatEmptyMergeCanBeIgnored(data);                TEST_FLUSH();
    requireThatMergeErrorCanBeIgnored(data);                TEST_FLUSH();
    requireThatMergeExceptionCanBeIgnored(data);            TEST_FLUSH();
    requireThatMergeAndThrowCanBeIgnored(data);             TEST_FLUSH();
    requireThatAllocServiceCanBeIgnored(data);              TEST_FLUSH();
    requireThatDepthLimitCanBeIgnored(data);                TEST_FLUSH();

    // This needs to be last because it changes timeouts:
    testTimeout(data);                   TEST_FLUSH();

    TEST_DONE();
}

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

void
Test::testNoRoutingTable(TestData &data)
{
    Result res = data._srcSession->send(createMessage("msg"), "foo");
    EXPECT_TRUE(!res.isAccepted());
    EXPECT_EQUAL((uint32_t)ErrorCode::ILLEGAL_ROUTE, res.getError().getCode());
    Message::UP msg = res.getMessage();
    EXPECT_TRUE(msg);
}

void
Test::testUnknownRoute(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("foo", "bar"))));
    Result res = data._srcSession->send(createMessage("msg"), "baz");
    EXPECT_TRUE(!res.isAccepted());
    EXPECT_EQUAL((uint32_t)ErrorCode::ILLEGAL_ROUTE, res.getError().getCode());
    Message::UP msg = res.getMessage();
    EXPECT_TRUE(msg);
}

void
Test::testNoRoute(TestData &data)
{
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route()).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

void
Test::testRecognizeHopName(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("dst", "dst/session"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testRecognizeRouteDirective(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("dst").addHop("dst/session"))
                                                           .addHop(HopSpec("dir", "route:dst"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dir")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testRecognizeRouteName(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("dst").addHop("dst/session"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testHopResolutionOverflow(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("foo", "bar"))
                                                           .addHop(HopSpec("bar", "foo"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("foo")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

void
Test::testRouteResolutionOverflow(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("foo").addHop("route:foo"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), "foo").isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

void
Test::testInsertRoute(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("foo").addHop("dst/session").addHop("bar"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("route:foo baz")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    EXPECT_EQUAL(2u, msg->getRoute().getNumHops());
    EXPECT_EQUAL("bar", msg->getRoute().getHop(0).toString());
    EXPECT_EQUAL("baz", msg->getRoute().getHop(1).toString());
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testErrorDirective(TestData &data)
{
    Route route = Route::parse("foo/bar/baz");
    route.getHop(0).setDirective(1, IHopDirective::SP(new ErrorDirective("err")));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), route).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
    EXPECT_EQUAL("err", reply->getError(0).getMessage());
}

void
Test::testSelectError(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom: ]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    LOG(info, "testSelectError trace=%s", reply->getTrace().toString().c_str());
    LOG(info, "testSelectError error=%s", reply->getError(0).toString().c_str());
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

void
Test::testSelectNone(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::NO_SERVICES_FOR_ROUTE, reply->getError(0).getCode());
}

void
Test::testSelectOne(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testResend1(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err1"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    reply.reset(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err2"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("[APP_TRANSIENT_ERROR @ localhost]: err1")
                         .add("-[APP_TRANSIENT_ERROR @ localhost]: err1")
                         .add("[APP_TRANSIENT_ERROR @ localhost]: err2")
                         .add("-[APP_TRANSIENT_ERROR @ localhost]: err2"),
                         reply->getTrace()));
}

void
Test::testResend2(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err1"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    reply.reset(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err2"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Source session accepted a 3 byte message. 1 message(s) now pending.")
                         .add("Running routing policy 'Custom'.")
                         .add("Selecting { 'dst/session' }.")
                         .add("Component 'dst/session' selected by policy 'Custom'.")
                         .add("Resolving 'dst/session'.")
                         .add("Sending message (version ${VERSION}) from client to 'dst/session'")
                         .add("Message (type 1) received at 'dst' for session 'session'.")
                         .add("[APP_TRANSIENT_ERROR @ localhost]: err1")
                         .add("Sending reply (version ${VERSION}) from 'dst'.")
                         .add("Reply (type 0) received at client.")
                         .add("Routing policy 'Custom' merging replies.")
                         .add("Merged { 'dst/session' }.")
                         .add("Message scheduled for retry 1 in 0.000 seconds.")
                         .add("Resender resending message.")
                         .add("Running routing policy 'Custom'.")
                         .add("Selecting { 'dst/session' }.")
                         .add("Component 'dst/session' selected by policy 'Custom'.")
                         .add("Resolving 'dst/session'.")
                         .add("Sending message (version ${VERSION}) from client to 'dst/session'")
                         .add("Message (type 1) received at 'dst' for session 'session'.")
                         .add("[APP_TRANSIENT_ERROR @ localhost]: err2")
                         .add("Sending reply (version ${VERSION}) from 'dst'.")
                         .add("Reply (type 0) received at client.")
                         .add("Routing policy 'Custom' merging replies.")
                         .add("Merged { 'dst/session' }.")
                         .add("Message scheduled for retry 2 in 0.000 seconds.")
                         .add("Resender resending message.")
                         .add("Running routing policy 'Custom'.")
                         .add("Selecting { 'dst/session' }.")
                         .add("Component 'dst/session' selected by policy 'Custom'.")
                         .add("Resolving 'dst/session'.")
                         .add("Sending message (version ${VERSION}) from client to 'dst/session'")
                         .add("Message (type 1) received at 'dst' for session 'session'.")
                         .add("Sending reply (version ${VERSION}) from 'dst'.")
                         .add("Reply (type 0) received at client.")
                         .add("Routing policy 'Custom' merging replies.")
                         .add("Merged { 'dst/session' }.")
                         .add("Source session received reply. 0 message(s) now pending."),
                         reply->getTrace()));
}

void
Test::testNoResend(TestData &data)
{
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err1"));
    data._dstSession->reply(std::move(reply));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::APP_TRANSIENT_ERROR, reply->getError(0).getCode());
}

void
Test::testSelectOnResend(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Selecting { 'dst/session' }.")
                         .add("[APP_TRANSIENT_ERROR @ localhost]")
                         .add("-[APP_TRANSIENT_ERROR @ localhost]")
                         .add("Merged { 'dst/session' }.")
                         .add("Selecting { 'dst/session' }.")
                         .add("Sending reply")
                         .add("Merged { 'dst/session' }."),
                         reply->getTrace()));
}

void
Test::testNoSelectOnResend(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(false)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Selecting { 'dst/session' }.")
                         .add("[APP_TRANSIENT_ERROR @ localhost]")
                         .add("-[APP_TRANSIENT_ERROR @ localhost]")
                         .add("Merged { 'dst/session' }.")
                         .add("-Selecting { 'dst/session' }.")
                         .add("Sending reply")
                         .add("Merged { 'dst/session' }."),
                         reply->getTrace()));
}

void
Test::testCanConsumeError(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(true, ErrorCode::NO_ADDRESS_FOR_SERVICE)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session,dst/unknown]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Selecting { 'dst/session', 'dst/unknown' }.")
                         .add("[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("Sending reply")
                         .add("Merged { 'dst/session', 'dst/unknown' }."),
                         reply->getTrace()));
}

void
Test::testCantConsumeError(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/unknown]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    printf("%s", reply->getTrace().toString().c_str());
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Selecting { 'dst/unknown' }.")
                         .add("[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("Merged { 'dst/unknown' }."),
                         reply->getTrace()));
}

void
Test::testNestedPolicies(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(true, ErrorCode::NO_ADDRESS_FOR_SERVICE)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:[Custom:dst/session],[Custom:dst/unknown]]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
}

void
Test::testRemoveReply(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new RemoveReplyPolicyFactory(
                                                                             true,
                                                                             UIntList().add(ErrorCode::NO_ADDRESS_FOR_SERVICE),
                                                                             0)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:[Custom:dst/session],[Custom:dst/unknown]]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("-[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("Sending message")
                         .add("-Sending message"),
                         reply->getTrace()));
}

void
Test::testSetReply(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Select", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(true, ErrorCode::APP_FATAL_ERROR)));
    simple.addPolicyFactory("SetReply", SimpleProtocol::IPolicyFactory::SP(new SetReplyPolicyFactory(true, UIntList().add(ErrorCode::APP_FATAL_ERROR))));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Select:[SetReply:foo],dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::APP_FATAL_ERROR, reply->getError(0).getCode());
    EXPECT_EQUAL("foo", reply->getError(0).getMessage());
}

void
Test::testResendSetAndReuseReply(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("ReuseReply", SimpleProtocol::IPolicyFactory::SP(new ReuseReplyPolicyFactory(
                                                                                 false,
                                                                                 UIntList().add(ErrorCode::APP_FATAL_ERROR))));
    simple.addPolicyFactory("SetReply", SimpleProtocol::IPolicyFactory::SP(new SetReplyPolicyFactory(
                                                                               false,
                                                                               UIntList().add(ErrorCode::APP_FATAL_ERROR))));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[ReuseReply:[SetReply:foo],dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "dst"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
}

void
Test::testResendSetAndRemoveReply(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("RemoveReply", SimpleProtocol::IPolicyFactory::SP(new RemoveReplyPolicyFactory(
                                                                                  false,
                                                                                  UIntList().add(ErrorCode::APP_TRANSIENT_ERROR),
                                                                                  0)));
    simple.addPolicyFactory("SetReply", SimpleProtocol::IPolicyFactory::SP(new SetReplyPolicyFactory(
                                                                               false,
                                                                               UIntList().add(ErrorCode::APP_TRANSIENT_ERROR).add(ErrorCode::APP_FATAL_ERROR))));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[RemoveReply:[SetReply:foo],dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::APP_FATAL_ERROR, reply->getError(0).getCode());
    EXPECT_EQUAL("foo", reply->getError(0).getMessage());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Resolving '[SetReply:foo]'.")
                         .add("Resolving 'dst/session'.")
                         .add("Resender resending message.")
                         .add("Resolving 'dst/session'.")
                         .add("Resolving '[SetReply:foo]'."),
                         reply->getTrace()));
}

void
Test::testHopIgnoresReply(TestData &data)
{
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("?dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_FATAL_ERROR, "dst"));
    data._dstSession->reply(std::move(reply));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Not waiting for a reply from 'dst/session'."),
                         reply->getTrace()));
}

void
Test::testHopBlueprintIgnoresReply(TestData &data)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("foo", "dst/session").setIgnoreResult(true))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("foo")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply(new EmptyReply());
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_FATAL_ERROR, "dst"));
    data._dstSession->reply(std::move(reply));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Not waiting for a reply from 'dst/session'."),
                         reply->getTrace()));
}

void
Test::testAcceptEmptyRoute(TestData &data)
{
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    const Route &route = msg->getRoute();
    EXPECT_EQUAL(0u, route.getNumHops());
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
}

void
Test::testAbortOnlyActiveNodes(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("Custom", SimpleProtocol::IPolicyFactory::SP(new CustomPolicyFactory(false)));
    simple.addPolicyFactory("SetReply", SimpleProtocol::IPolicyFactory::SP(new SetReplyPolicyFactory(
                                                                               false,
                                                                               UIntList()
                                                                               .add(ErrorCode::APP_TRANSIENT_ERROR)
                                                                               .add(ErrorCode::APP_TRANSIENT_ERROR)
                                                                               .add(ErrorCode::APP_FATAL_ERROR))));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:[SetReply:foo],?bar,dst/session]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(2u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::APP_FATAL_ERROR, reply->getError(0).getCode());
    EXPECT_EQUAL((uint32_t)ErrorCode::SEND_ABORTED, reply->getError(1).getCode());
}

void
Test::testUnknownPolicy(TestData &data)
{
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Unknown]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::UNKNOWN_POLICY, reply->getError(0).getCode());
}

void
Test::testSelectException(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("SelectException",
                            SimpleProtocol::IPolicyFactory::SP(
                                    new SelectExceptionPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"),
                                       Route::parse("[SelectException]"))
                .isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::POLICY_ERROR,
                 reply->getError(0).getCode());
    EXPECT_EQUAL("Policy 'SelectException' threw an exception; {test exception}",
                 reply->getError(0).getMessage());
}

void
Test::testMergeException(TestData &data)
{
    IProtocol::SP protocol(new SimpleProtocol());
    SimpleProtocol &simple = static_cast<SimpleProtocol&>(*protocol);
    simple.addPolicyFactory("MergeException",
                            SimpleProtocol::IPolicyFactory::SP(
                                    new MergeExceptionPolicyFactory()));
    data._srcServer.mb.putProtocol(protocol);
    Route route = Route::parse("[MergeException:dst/session]");
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), route)
                .isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(1u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::POLICY_ERROR,
                 reply->getError(0).getCode());
    EXPECT_EQUAL("Policy 'MergeException' threw an exception; {test exception}",
                 reply->getError(0).getMessage());
}

void 
Test::requireThatIgnoreFlagPersistsThroughHopLookup(TestData &data)
{
    setupRouting(data, RoutingTableSpec(SimpleProtocol::NAME).addHop(HopSpec("foo", "dst/unknown")));
    ASSERT_TRUE(testSend(data, "?foo"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void 
Test::requireThatIgnoreFlagPersistsThroughRouteLookup(TestData &data)
{
    setupRouting(data, RoutingTableSpec(SimpleProtocol::NAME).addRoute(RouteSpec("foo").addHop("dst/unknown")));
    ASSERT_TRUE(testSend(data, "?foo"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void 
Test::requireThatIgnoreFlagPersistsThroughPolicySelect(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndMerge("dst/unknown"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void 
Test::requireThatIgnoreFlagIsSerializedWithMessage(TestData &data)
{
    ASSERT_TRUE(testSend(data, "dst/session foo ?bar"));
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Route route = msg->getRoute();
    EXPECT_EQUAL(2u, route.getNumHops());
    Hop hop = route.getHop(0);
    EXPECT_EQUAL("foo", hop.toString());
    EXPECT_TRUE(!hop.getIgnoreResult());
    hop = route.getHop(1);
    EXPECT_EQUAL("?bar", hop.toString());
    EXPECT_TRUE(hop.getIgnoreResult());
    data._dstSession->acknowledge(std::move(msg));
    ASSERT_TRUE(testTrace(data, StringList().add("-Ignoring errors in reply.")));
}

void 
Test::requireThatIgnoreFlagDoesNotInterfere(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndMerge("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("-Ignoring errors in reply.")));
    ASSERT_TRUE(testAcknowledge(data));
}
                
void 
Test::requireThatEmptySelectionCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newEmptySelection());
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void
Test::requireThatSelectErrorCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectError(ErrorCode::APP_FATAL_ERROR));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void 
Test::requireThatSelectExceptionCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectException());
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void
Test::requireThatSelectAndThrowCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndThrow("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void
Test::requireThatEmptyMergeCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newEmptyMerge("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void 
Test::requireThatMergeErrorCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newMergeError("dst/session", ErrorCode::APP_FATAL_ERROR));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void 
Test::requireThatMergeExceptionCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newMergeException("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}
                
void 
Test::requireThatMergeAndThrowCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newMergeAndThrow("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void
Test::requireThatAllocServiceCanBeIgnored(TestData &data)
{
    ASSERT_TRUE(testSend(data, "?dst/unknown"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

void
Test::requireThatDepthLimitCanBeIgnored(TestData &data) 
{
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndMerge("[Custom]"));
    ASSERT_TRUE(testSend(data, "?[Custom]", 0));
    ASSERT_TRUE(testTrace(data, StringList()));
}

void
Test::testTimeout(TestData &data)
{
    data._retryPolicy->setEnabled(true);
    data._retryPolicy->setBaseDelay(0.01);
    data._srcSession->setTimeout(500ms);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/unknown")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQUAL(2u, reply->getNumErrors());
    EXPECT_EQUAL((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
    EXPECT_EQUAL((uint32_t)ErrorCode::TIMEOUT, reply->getError(1).getCode());
}
