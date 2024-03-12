// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>

#include <memory>
#include <utility>
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
                      std::vector<uint32_t> consumableErrors,
                      std::vector<Route> routes,
                      uint32_t idxRemove);
    void merge(RoutingContext &ctx) override;
};

RemoveReplyPolicy::RemoveReplyPolicy(bool selectOnRetry,
                                     std::vector<uint32_t> consumableErrors,
                                     std::vector<Route> routes,
                                     uint32_t idxRemove) :
    CustomPolicy::CustomPolicy(selectOnRetry, std::move(consumableErrors), std::move(routes)),
    _idxRemove(idxRemove)
{ }

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
                             std::vector<uint32_t> consumableErrors,
                             uint32_t idxRemove) noexcept;
    ~RemoveReplyPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override;
};

RemoveReplyPolicyFactory::~RemoveReplyPolicyFactory() = default;

RemoveReplyPolicyFactory::RemoveReplyPolicyFactory(bool selectOnRetry,
                                                   std::vector<uint32_t> consumableErrors,
                                                   uint32_t idxRemove) noexcept
    : _selectOnRetry(selectOnRetry),
      _consumableErrors(std::move(consumableErrors)),
      _idxRemove(idxRemove)
{
    // empty
}

IRoutingPolicy::UP
RemoveReplyPolicyFactory::create(const string &param)
{
    return std::make_unique<RemoveReplyPolicy>(_selectOnRetry, _consumableErrors,
                                               CustomPolicyFactory::parseRoutes(param), _idxRemove);
}

class ReuseReplyPolicy : public CustomPolicy {
private:
    std::vector<uint32_t> _errorMask;
public:
    ReuseReplyPolicy(bool selectOnRetry, std::vector<uint32_t> errorMask, std::vector<Route> routes);
    void merge(RoutingContext &ctx) override;
};

ReuseReplyPolicy::ReuseReplyPolicy(bool selectOnRetry, std::vector<uint32_t> errorMask, std::vector<Route> routes) :
    CustomPolicy::CustomPolicy(selectOnRetry, errorMask, std::move(routes)),
    _errorMask(std::move(errorMask))
{ }

void
ReuseReplyPolicy::merge(RoutingContext &ctx)
{
    auto ret = std::make_unique<EmptyReply>();
    uint32_t idx = 0;
    int idxFirstOk = -1;
    for (RoutingNodeIterator it = ctx.getChildIterator(); it.isValid(); it.next(), ++idx) {
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
    return std::make_unique<ReuseReplyPolicy>(_selectOnRetry, _errorMask, CustomPolicyFactory::parseRoutes(param));
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
        ctx.setReply(std::make_unique<EmptyReply>());
    }
    ctx.setSelectOnRetry(_selectOnRetry);
}

void
SetReplyPolicy::merge(RoutingContext &ctx)
{
    auto reply = std::make_unique<EmptyReply>();
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
    return std::make_unique<SetReplyPolicy>(_selectOnRetry, _errors, param);
}

class TestException : public std::exception {
    virtual const char* what() const noexcept override {
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
    IRoutingPolicy::UP create(const string &) override {
        return std::make_unique<SelectExceptionPolicy>();
    }
};

SelectExceptionPolicyFactory::~SelectExceptionPolicyFactory() = default;

class MergeExceptionPolicy : public IRoutingPolicy {
private:
    const string _select;

public:
    explicit MergeExceptionPolicy(const string &param)
        : _select(param)
    { }

    void select(RoutingContext &ctx) override {
        ctx.addChild(Route::parse(_select));
    }

    void merge(RoutingContext &) override {
        throw TestException();
    }
};

class MergeExceptionPolicyFactory : public SimpleProtocol::IPolicyFactory {
public:
    ~MergeExceptionPolicyFactory() override;
    IRoutingPolicy::UP create(const string &param) override {
        return std::make_unique<MergeExceptionPolicy>(param);
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

    MyPolicyFactory(const string &selectRoute, uint32_t &selectError, bool selectException,
                    bool mergeFromChild, uint32_t mergeError, bool mergeException) noexcept;
    ~MyPolicyFactory() override;

    IRoutingPolicy::UP create(const string &param) override;

    static MyPolicyFactory::SP newInstance(const string &selectRoute, uint32_t selectError, bool selectException,
                                           bool mergeFromChild, uint32_t mergeError, bool mergeException)
    {
        return std::make_shared<MyPolicyFactory>(selectRoute, selectError, selectException,
                                                 mergeFromChild, mergeError, mergeException);
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

MyPolicyFactory::MyPolicyFactory(const string &selectRoute, uint32_t &selectError, bool selectException,
                                 bool mergeFromChild, uint32_t mergeError, bool mergeException) noexcept
    : _selectRoute(selectRoute),
      _selectError(selectError),
      _selectException(selectException),
      _mergeFromChild(mergeFromChild),
      _mergeError(mergeError),
      _mergeException(mergeException)
{ }

MyPolicyFactory::~MyPolicyFactory() = default;

class MyPolicy : public IRoutingPolicy {
private:
    const MyPolicyFactory &_parent;

public:
    explicit MyPolicy(const MyPolicyFactory &parent) :
        _parent(parent)
    {}

    void select(RoutingContext &ctx) override
    {
        if (!_parent._selectRoute.empty()) {
            ctx.addChild(Route::parse(_parent._selectRoute));
        }
        if (_parent._selectError != ErrorCode::NONE) {
            auto reply = std::make_unique<EmptyReply>();
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
            auto reply = std::make_unique<EmptyReply>();
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
MyPolicyFactory::create(const string &)
{
    return std::make_unique<MyPolicy>(*this);
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

class RoutingTest : public testing::Test {
protected:
    static const duration RECEPTOR_TIMEOUT;
    static std::shared_ptr<TestData> _data;
    static bool                      _force_teardown;
    RoutingTest();
    ~RoutingTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    void SetUp() override;
    void TearDown() override;
    static Message::UP createMessage(const string &msg, uint32_t level = 9);
    static void setupRouting(TestData &data, RoutingTableSpec && spec);
    static void setupPolicy(TestData &data, const string &policyName,
                            SimpleProtocol::IPolicyFactory::SP policy);
    static bool testAcknowledge(TestData &data);
    static bool testSend(TestData &data, const string &route, uint32_t level = 9);
    static bool testTrace(TestData &data, const std::vector<string> &expected);
    static bool testTrace(const std::vector<string> &expected, const Trace &trace);
};

const duration RoutingTest::RECEPTOR_TIMEOUT = 120s;
std::shared_ptr<TestData> RoutingTest::_data;
bool RoutingTest::_force_teardown = false;

RoutingTest::RoutingTest() = default;
RoutingTest::~RoutingTest() = default;

void
RoutingTest::SetUpTestSuite()
{
    _data = std::make_shared<TestData>();
    ASSERT_TRUE(_data->start());
}

void
RoutingTest::TearDownTestSuite()
{
    _data.reset();
}

void
RoutingTest::SetUp()
{
    if (!_data) {
        _data = std::make_shared<TestData>();
        ASSERT_TRUE(_data->start());
    }
}

void
RoutingTest::TearDown()
{
    if (_force_teardown) {
        _force_teardown = false;
        _data.reset();
    }
}

Message::UP
RoutingTest::createMessage(const string &msg, uint32_t level)
{
    auto ret = std::make_unique<SimpleMessage>(msg);
    ret->getTrace().setLevel(level);
    return ret;
}

void
RoutingTest::setupRouting(TestData &data, RoutingTableSpec && spec)
{
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(std::move(spec)));
}

void
RoutingTest::setupPolicy(TestData &data, const string &policyName,
                  SimpleProtocol::IPolicyFactory::SP policy)
{
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory(policyName, std::move(policy));
    data._srcServer.mb.putProtocol(protocol);
}

bool
RoutingTest::testAcknowledge(TestData &data)
{
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    bool failed = false;
    EXPECT_TRUE(msg) << (failed = true, "");
    if (failed) {
        return false;
    }
    data._dstSession->acknowledge(std::move(msg));
    return true;
}

bool
RoutingTest::testSend(TestData &data, const string &route, uint32_t level)
{
    Message::UP msg = createMessage("msg", level);
    msg->setRoute(Route::parse(route));
    return data._srcSession->send(std::move(msg)).isAccepted();
}

bool
RoutingTest::testTrace(TestData &data, const std::vector<string> &expected)
{
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    bool failed = false;
    EXPECT_TRUE(reply) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_FALSE(reply->hasErrors()) << (failed = true, "");
    if (failed) {
        return false;
    }
    return testTrace(expected, reply->getTrace());
}

bool
RoutingTest::testTrace(const std::vector<string> &expected, const Trace &trace)
{
    const string& version = vespalib::Vtag::currentVersion.toString();
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
            bool failed = false;
            EXPECT_TRUE(actual.find(str, pos) == string::npos) << (failed = true, "");
            if (failed) {
                LOG(error, "Line %d '%s' not expected.", i, str.c_str());
                return false;
            }
        } else {
            pos = actual.find(line, pos);
            bool failed = false;
            EXPECT_TRUE(pos != string::npos) << (failed = true, "");
            if (failed) {
                LOG(error, "Line %d '%s' missing.", i, line.c_str());
                return false;
            }
            ++pos;
        }
    }
    return true;
}

////////////////////////////////////////////////////////////////////////////////
//
// Tests
//
////////////////////////////////////////////////////////////////////////////////

TEST_F(RoutingTest, test_no_routing_table)
{
    auto& data = *_data;
    Result res = data._srcSession->send(createMessage("msg"), "foo");
    EXPECT_FALSE(res.isAccepted());
    EXPECT_EQ((uint32_t)ErrorCode::ILLEGAL_ROUTE, res.getError().getCode());
    Message::UP msg = res.getMessage();
    EXPECT_TRUE(msg);
}

TEST_F(RoutingTest, test_unknown_route)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("foo", "bar"))));
    Result res = data._srcSession->send(createMessage("msg"), "baz");
    EXPECT_FALSE(res.isAccepted());
    EXPECT_EQ((uint32_t)ErrorCode::ILLEGAL_ROUTE, res.getError().getCode());
    Message::UP msg = res.getMessage();
    EXPECT_TRUE(msg);
}

TEST_F(RoutingTest, test_no_route)
{
    auto& data = *_data;
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route()).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_recognize_hop_name)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("dst", "dst/session"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
}

TEST_F(RoutingTest, test_recognize_route_directive)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("dst").addHop("dst/session"))
                                                           .addHop(HopSpec("dir", "route:dst"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dir")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
}

TEST_F(RoutingTest, test_recognize_route_name)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("dst").addHop("dst/session"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
}

TEST_F(RoutingTest, test_hop_resolution_overflow)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(HopSpec("foo", "bar"))
                                                           .addHop(HopSpec("bar", "foo"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("foo")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_route_resolution_overflow)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("foo").addHop("route:foo"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), "foo").isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_insert_route)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addRoute(RouteSpec("foo").addHop("dst/session").addHop("bar"))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("route:foo baz")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    EXPECT_EQ(2u, msg->getRoute().getNumHops());
    EXPECT_EQ("bar", msg->getRoute().getHop(0).toString());
    EXPECT_EQ("baz", msg->getRoute().getHop(1).toString());
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
}

TEST_F(RoutingTest, test_error_directive)
{
    auto& data = *_data;
    Route route = Route::parse("foo/bar/baz");
    route.getHop(0).setDirective(1, std::make_shared<ErrorDirective>("err"));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), route).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
    EXPECT_EQ("err", reply->getError(0).getMessage());
}

TEST_F(RoutingTest, test_select_error)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom: ]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    LOG(info, "testSelectError trace=%s", reply->getTrace().toString().c_str());
    LOG(info, "testSelectError error=%s", reply->getError(0).toString().c_str());
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::ILLEGAL_ROUTE, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_select_none)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::NO_SERVICES_FOR_ROUTE, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_select_one)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
}

TEST_F(RoutingTest, test_resend_1)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err1"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err2"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("[APP_TRANSIENT_ERROR @ localhost]: err1")
                         .add("-[APP_TRANSIENT_ERROR @ localhost]: err1")
                         .add("[APP_TRANSIENT_ERROR @ localhost]: err2")
                         .add("-[APP_TRANSIENT_ERROR @ localhost]: err2"),
                         reply->getTrace()));
}

TEST_F(RoutingTest, test_resend_2)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err1"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err2"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
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

TEST_F(RoutingTest, test_no_resend)
{
    auto& data = *_data;
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err1"));
    data._dstSession->reply(std::move(reply));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::APP_TRANSIENT_ERROR, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_select_on_resend)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
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

TEST_F(RoutingTest, test_no_select_on_resend)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>(false));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "err"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
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

TEST_F(RoutingTest, test_can_consume_error)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>(true, ErrorCode::NO_ADDRESS_FOR_SERVICE));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/session,dst/unknown]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Selecting { 'dst/session', 'dst/unknown' }.")
                         .add("[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("Sending reply")
                         .add("Merged { 'dst/session', 'dst/unknown' }."),
                         reply->getTrace()));
}

TEST_F(RoutingTest, test_cant_consume_error)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:dst/unknown]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    printf("%s", reply->getTrace().toString().c_str());
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Selecting { 'dst/unknown' }.")
                         .add("[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("Merged { 'dst/unknown' }."),
                         reply->getTrace()));
}

TEST_F(RoutingTest, test_nested_policies)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>(true, ErrorCode::NO_ADDRESS_FOR_SERVICE));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:[Custom:dst/session],[Custom:dst/unknown]]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_remove_reply)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<RemoveReplyPolicyFactory>(true, UIntList().add(ErrorCode::NO_ADDRESS_FOR_SERVICE), 0));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:[Custom:dst/session],[Custom:dst/unknown]]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("-[NO_ADDRESS_FOR_SERVICE @ localhost]")
                         .add("Sending message")
                         .add("-Sending message"),
                         reply->getTrace()));
}

TEST_F(RoutingTest, test_set_reply)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Select", std::make_shared<CustomPolicyFactory>(true, ErrorCode::APP_FATAL_ERROR));
    protocol->addPolicyFactory("SetReply", std::make_shared<SetReplyPolicyFactory>(true, UIntList().add(ErrorCode::APP_FATAL_ERROR)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(false);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Select:[SetReply:foo],dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::APP_FATAL_ERROR, reply->getError(0).getCode());
    EXPECT_EQ("foo", reply->getError(0).getMessage());
}

TEST_F(RoutingTest, test_resend_set_and_reuse_reply)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("ReuseReply", std::make_shared<ReuseReplyPolicyFactory>(false, UIntList().add(ErrorCode::APP_FATAL_ERROR)));
    protocol->addPolicyFactory("SetReply", std::make_shared<SetReplyPolicyFactory>(false, UIntList().add(ErrorCode::APP_FATAL_ERROR)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[ReuseReply:[SetReply:foo],dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_TRANSIENT_ERROR, "dst"));
    data._dstSession->reply(std::move(reply));
    msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
}

TEST_F(RoutingTest, test_resend_set_and_remove_reply)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("RemoveReply", std::make_shared<RemoveReplyPolicyFactory>(false, UIntList().add(ErrorCode::APP_TRANSIENT_ERROR), 0));
    protocol->addPolicyFactory("SetReply", std::make_shared<SetReplyPolicyFactory>(false, UIntList().add(ErrorCode::APP_TRANSIENT_ERROR).add(ErrorCode::APP_FATAL_ERROR)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[RemoveReply:[SetReply:foo],dst/session]")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::APP_FATAL_ERROR, reply->getError(0).getCode());
    EXPECT_EQ("foo", reply->getError(0).getMessage());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Resolving '[SetReply:foo]'.")
                         .add("Resolving 'dst/session'.")
                         .add("Resender resending message.")
                         .add("Resolving 'dst/session'.")
                         .add("Resolving '[SetReply:foo]'."),
                         reply->getTrace()));
}

TEST_F(RoutingTest, test_hop_ignores_reply)
{
    auto& data = *_data;
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("?dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_FATAL_ERROR, "dst"));
    data._dstSession->reply(std::move(reply));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Not waiting for a reply from 'dst/session'."),
                         reply->getTrace()));
}

TEST_F(RoutingTest, test_hop_blueprint_ignores_reply)
{
    auto& data = *_data;
    data._srcServer.mb.setupRouting(RoutingSpec().addTable(RoutingTableSpec(SimpleProtocol::NAME)
                                                           .addHop(std::move(HopSpec("foo", "dst/session").setIgnoreResult(true)))));
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("foo")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Reply::UP reply = std::make_unique<EmptyReply>();
    reply->swapState(*msg);
    reply->addError(Error(ErrorCode::APP_FATAL_ERROR, "dst"));
    data._dstSession->reply(std::move(reply));
    reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_FALSE(reply->hasErrors());
    EXPECT_TRUE(testTrace(StringList()
                         .add("Not waiting for a reply from 'dst/session'."),
                         reply->getTrace()));
}

TEST_F(RoutingTest, test_accept_empty_route)
{
    auto& data = *_data;
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/session")).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    const Route &route = msg->getRoute();
    EXPECT_EQ(0u, route.getNumHops());
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
}

TEST_F(RoutingTest, test_abort_only_active_nodes)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("Custom", std::make_shared<CustomPolicyFactory>(false));
    protocol->addPolicyFactory("SetReply", std::make_shared<SetReplyPolicyFactory>(false,
                                                                                   UIntList().add(ErrorCode::APP_TRANSIENT_ERROR)
                                                                                             .add(ErrorCode::APP_TRANSIENT_ERROR)
                                                                                             .add(ErrorCode::APP_FATAL_ERROR)));
    data._srcServer.mb.putProtocol(protocol);
    data._retryPolicy->setEnabled(true);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Custom:[SetReply:foo],?bar,dst/session]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(2u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::APP_FATAL_ERROR, reply->getError(0).getCode());
    EXPECT_EQ((uint32_t)ErrorCode::SEND_ABORTED, reply->getError(1).getCode());
}

TEST_F(RoutingTest, test_unknown_policy)
{
    auto& data = *_data;
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[Unknown]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::UNKNOWN_POLICY, reply->getError(0).getCode());
}

TEST_F(RoutingTest, test_select_exception)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("SelectException", std::make_shared<SelectExceptionPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("[SelectException]")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::POLICY_ERROR, reply->getError(0).getCode());
    EXPECT_EQ("Policy 'SelectException' threw an exception; {test exception}",
              reply->getError(0).getMessage());
}

TEST_F(RoutingTest, test_merge_exception)
{
    auto& data = *_data;
    auto protocol = std::make_shared<SimpleProtocol>();
    protocol->addPolicyFactory("MergeException", std::make_shared<MergeExceptionPolicyFactory>());
    data._srcServer.mb.putProtocol(protocol);
    Route route = Route::parse("[MergeException:dst/session]");
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), route).isAccepted());
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    data._dstSession->acknowledge(std::move(msg));
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(1u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::POLICY_ERROR,
                 reply->getError(0).getCode());
    EXPECT_EQ("Policy 'MergeException' threw an exception; {test exception}",
              reply->getError(0).getMessage());
}

TEST_F(RoutingTest, require_that_ignore_flag_persists_through_hop_lookup)
{
    auto& data = *_data;
    setupRouting(data, RoutingTableSpec(SimpleProtocol::NAME).addHop(HopSpec("foo", "dst/unknown")));
    ASSERT_TRUE(testSend(data, "?foo"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_ignore_flag_persists_through_route_lookup)
{
    auto& data = *_data;
    setupRouting(data, RoutingTableSpec(SimpleProtocol::NAME).addRoute(RouteSpec("foo").addHop("dst/unknown")));
    ASSERT_TRUE(testSend(data, "?foo"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_ignore_flag_persists_through_policy_select)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndMerge("dst/unknown"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_ignore_flag_is_serialized_with_message)
{
    auto& data = *_data;
    ASSERT_TRUE(testSend(data, "dst/session foo ?bar"));
    Message::UP msg = data._dstHandler.getMessage(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(msg);
    Route route = msg->getRoute();
    EXPECT_EQ(2u, route.getNumHops());
    Hop hop = route.getHop(0);
    EXPECT_EQ("foo", hop.toString());
    EXPECT_FALSE(hop.getIgnoreResult());
    hop = route.getHop(1);
    EXPECT_EQ("?bar", hop.toString());
    EXPECT_TRUE(hop.getIgnoreResult());
    data._dstSession->acknowledge(std::move(msg));
    ASSERT_TRUE(testTrace(data, StringList().add("-Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_ignore_flag_does_not_interfere)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndMerge("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("-Ignoring errors in reply.")));
    ASSERT_TRUE(testAcknowledge(data));
}

TEST_F(RoutingTest, require_that_empty_selection_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newEmptySelection());
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_select_error_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectError(ErrorCode::APP_FATAL_ERROR));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_select_exception_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectException());
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_select_and_throw_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndThrow("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_empty_merge_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newEmptyMerge("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_merge_error_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newMergeError("dst/session", ErrorCode::APP_FATAL_ERROR));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_merge_exception_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newMergeException("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_merge_and_throw_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newMergeAndThrow("dst/session"));
    ASSERT_TRUE(testSend(data, "?[Custom]"));
    ASSERT_TRUE(testAcknowledge(data));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_alloc_service_can_be_ignored)
{
    auto& data = *_data;
    ASSERT_TRUE(testSend(data, "?dst/unknown"));
    ASSERT_TRUE(testTrace(data, StringList().add("Ignoring errors in reply.")));
}

TEST_F(RoutingTest, require_that_depth_limit_can_be_ignored)
{
    auto& data = *_data;
    setupPolicy(data, "Custom", MyPolicyFactory::newSelectAndMerge("[Custom]"));
    ASSERT_TRUE(testSend(data, "?[Custom]", 0));
    ASSERT_TRUE(testTrace(data, StringList()));
}

TEST_F(RoutingTest, test_timeout)
{
    auto& data = *_data;
    // Force teardown after this test case since timeouts have been changed.
    _force_teardown = true;
    data._retryPolicy->setEnabled(true);
    data._retryPolicy->setBaseDelay(0.01);
    data._srcSession->setTimeout(500ms);
    EXPECT_TRUE(data._srcSession->send(createMessage("msg"), Route::parse("dst/unknown")).isAccepted());
    Reply::UP reply = data._srcHandler.getReply(RECEPTOR_TIMEOUT);
    ASSERT_TRUE(reply);
    EXPECT_EQ(2u, reply->getNumErrors());
    EXPECT_EQ((uint32_t)ErrorCode::NO_ADDRESS_FOR_SERVICE, reply->getError(0).getCode());
    EXPECT_EQ((uint32_t)ErrorCode::TIMEOUT, reply->getError(1).getCode());
}

GTEST_MAIN_RUN_ALL_TESTS()
