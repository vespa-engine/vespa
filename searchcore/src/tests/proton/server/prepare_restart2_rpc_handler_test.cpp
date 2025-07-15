// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/prepare_restart2_rpc_handler.h>

#include <vespa/fnet/connection.h>
#include <vespa/fnet/ipacketstreamer.h>
#include <vespa/fnet/iserveradapter.h>
#include <vespa/fnet/transport.h>
#include <vespa/searchcore/proton/flushengine/flush_history.h>
#include <vespa/searchcore/proton/flushengine/flush_strategy_id_notifier.h>
#include <vespa/searchcore/proton/server/detached_rpc_requests_owner.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <atomic>
#include <cassert>
#include <iostream>

using proton::DetachedRpcRequestsOwner;
using proton::PrepareRestart2RpcHandler;
using proton::flushengine::FlushHistory;
using proton::flushengine::FlushStrategyIdNotifier;
using std::chrono::steady_clock;
using vespalib::Slime;
using vespalib::slime::JsonFormat;

namespace {

struct DummyAdapter : FNET_IServerAdapter {
    bool InitChannel(FNET_Channel *, uint32_t) override { return false; }
};

struct DummyStreamer : FNET_IPacketStreamer {
    bool GetPacketInfo(FNET_DataBuffer *, uint32_t *, uint32_t *, uint32_t *, bool *) override { return false; }

    FNET_Packet *Decode(FNET_DataBuffer *, uint32_t, uint32_t, FNET_Context) override { return nullptr; }

    void Encode(FNET_Packet *, uint32_t, FNET_DataBuffer *) override {}
};

std::string as_string(const FRT_StringValue& v)
{
    return {v._str, v._len};
}

/*
 * Base class that supports waiting for destruction.
 */
class DestructionFutureFactoryBase {
    std::promise<void>       _destruction_promise;
    std::shared_future<void> _destruction_future;
public:
    DestructionFutureFactoryBase();
    virtual ~DestructionFutureFactoryBase();
    [[nodiscard]] std::shared_future<void> get_destruction_future() const noexcept { return _destruction_future; }
};

DestructionFutureFactoryBase::DestructionFutureFactoryBase()
    : _destruction_promise(),
      _destruction_future(_destruction_promise.get_future().share())
{
}

DestructionFutureFactoryBase::~DestructionFutureFactoryBase()
{
    _destruction_promise.set_value();
}

/*
 * Wrapper class that supports waiting for destruction.
 */
template <class B>
struct DestructionFutureFactory : public DestructionFutureFactoryBase,
                                  public B {
    using B::B;
    ~DestructionFutureFactory() override;
};

template <class B>
DestructionFutureFactory<B>::~DestructionFutureFactory() = default;

/*
 * Shared context for DestructGuard, used to detect (premature) destruction
 */
class DestructGuardContext {
    std::atomic<bool> _destructed;
    std::atomic<bool> _allow_destruct;
public:
    DestructGuardContext() noexcept
        : _destructed(false),
          _allow_destruct(false)
    {}
    void set_destructed() noexcept;
    void set_allow_destruct() noexcept { _allow_destruct = true; }
    [[nodiscard]] bool get_destructed() const noexcept { return _destructed; }
};

void
DestructGuardContext::set_destructed() noexcept
{
    assert(_allow_destruct);
    _destructed = true;
}

/*
 * Base class used to detect (premature) destruction.
 */
class DestructGuardBase {
    std::shared_ptr<DestructGuardContext> _destruct_guard_context;
public:
    DestructGuardBase();
    virtual ~DestructGuardBase();
    [[nodiscard]] const std::shared_ptr<DestructGuardContext>& destruct_guard_context() const noexcept {
        return _destruct_guard_context;
    }
};

DestructGuardBase::DestructGuardBase()
    : _destruct_guard_context(std::make_shared<DestructGuardContext>())
{
}

DestructGuardBase::~DestructGuardBase()
{
    _destruct_guard_context->set_destructed();
}

/*
 * Wrapper class used to detect (premature) destruction.
 */
template <class B>
struct DestructGuard : public B,
                       public DestructGuardBase {
    using B::B;
    ~DestructGuard() override;
};

template <typename B>
DestructGuard<B>::~DestructGuard() = default;

struct MyReturnHandler : public FRT_IReturnHandler
{
    std::shared_ptr<DestructGuardContext>   _conn_destruct_guard_context;
    vespalib::ref_counted<FNET_Connection>& _conn;
    std::shared_ptr<DestructGuardContext>   _req_destruct_guard_context;
    vespalib::ref_counted<FRT_RPCRequest>   _req;
    bool                                    _returned;
    bool                                    _detached;
    bool                                    _success;
    std::string                             _result;
    Slime                                   _slime;

    MyReturnHandler(std::shared_ptr<DestructGuardContext> conn_destruct_guard_context,
                   vespalib::ref_counted<FNET_Connection>& conn);
    ~MyReturnHandler() override;
    void HandleReturn() override;
    FNET_Connection* GetConnection() override;
    void alloc_req();
    void check_conn() const;
    void check_req();
    [[nodiscard]] bool has_returned() const noexcept { return _returned; }
    [[nodiscard]] bool has_detached() const noexcept { return _detached; }
    [[nodiscard]] bool req_success() const noexcept { return _success; }
    [[nodiscard]] std::string result() const { return _result; }
};

MyReturnHandler::MyReturnHandler(std::shared_ptr<DestructGuardContext> conn_destruct_guard_context,
                                 vespalib::ref_counted<FNET_Connection>& conn)
    : FRT_IReturnHandler(),
      _conn_destruct_guard_context(std::move(conn_destruct_guard_context)),
      _conn(conn),
      _req_destruct_guard_context(),
      _req(),
      _returned(false),
      _detached(false),
      _success(false),
      _result()
{
    alloc_req();
}

MyReturnHandler::~MyReturnHandler()
{
    check_conn();
    check_req();
    if (_req_destruct_guard_context) {
        _req_destruct_guard_context->set_allow_destruct();
    }
}

void
MyReturnHandler::HandleReturn()
{
    _returned = true;
    auto& v = *_req->GetReturn();
    ASSERT_EQ("bs", std::string(v.GetTypeString(), v.GetNumValues()));
    _success = (v[0]._intval8 == 1);
    _result = as_string(v[1]._string);
    _slime = Slime();
    EXPECT_EQ(_result.size(), JsonFormat::decode(_result, _slime));
    _req->internal_subref(); // Account for handover semantics
}

FNET_Connection*
MyReturnHandler::GetConnection()
{
    return _conn.get();
}

void
MyReturnHandler::alloc_req()
{
    if (_req_destruct_guard_context) {
        _req_destruct_guard_context->set_allow_destruct();
    }
    auto req = vespalib::make_ref_counted<DestructGuard<FRT_RPCRequest>>();
    _req_destruct_guard_context = req->destruct_guard_context();
    _req = std::move(req);
    _req->SetDetachedPT(&_detached);
    _req->SetReturnHandler(this);
    _returned = false;
    _detached = false;
}

void
MyReturnHandler::check_conn() const
{
    if (_conn_destruct_guard_context->get_destructed()) {
        _conn.internal_detach(); // All references lost, connection destroyed
    }
    ASSERT_TRUE(_conn);
    EXPECT_EQ(1, _conn->count_refs());
}

void
MyReturnHandler::check_req()
{
    if (_req_destruct_guard_context->get_destructed()) {
        _req.internal_detach(); // All references lost, request destroyed
    }
    ASSERT_TRUE(_req);
    EXPECT_EQ(1, _req->count_refs());
}

std::shared_ptr<FlushHistory>
make_flush_history()
{
   auto history = std::make_shared<FlushHistory>("memory", 200, 3);
   return history;
}

std::shared_ptr<FlushStrategyIdNotifier>
make_flush_strategy_id_notifier()
{
    auto notifier = std::make_shared<FlushStrategyIdNotifier>(200);
    return notifier;
}

}

class PrepareRestart2RpcHandlerTest : public ::testing::Test
{
protected:
    static std::unique_ptr<FNET_Transport>    _transport;
    DummyAdapter                              _dummy_adapter;
    DummyStreamer                             _dummy_streamer;
    std::shared_ptr<DestructGuardContext>     _conn_destruct_guard_context;
    vespalib::ref_counted<FNET_Connection>    _conn;
    std::unique_ptr<MyReturnHandler>          _return_handler;
    std::shared_ptr<DetachedRpcRequestsOwner> _detached_rpc_requests_owner;
    std::shared_ptr<FlushStrategyIdNotifier>  _notifier;
    std::shared_ptr<FlushHistory>             _history;
    PrepareRestart2RpcHandlerTest();
    ~PrepareRestart2RpcHandlerTest() override;
    void SetUp() override;
    void TearDown() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    std::shared_future<void> test_handler(uint32_t wait_strategy_id, steady_clock::duration timeout);
    void expect_result(bool expect_success);
    void expect_no_result();
};

std::unique_ptr<FNET_Transport> PrepareRestart2RpcHandlerTest::_transport;

PrepareRestart2RpcHandlerTest::PrepareRestart2RpcHandlerTest()
    : ::testing::Test(),
      _dummy_adapter(),
      _dummy_streamer(),
      _conn_destruct_guard_context(),
      _conn(),
      _return_handler(),
      _detached_rpc_requests_owner(std::make_shared<DetachedRpcRequestsOwner>()),
      _notifier(make_flush_strategy_id_notifier()),
      _history(make_flush_history())
{
}

PrepareRestart2RpcHandlerTest::~PrepareRestart2RpcHandlerTest() = default;

void
PrepareRestart2RpcHandlerTest::SetUp()
{
    using Conn = DestructGuard<FNET_Connection>;
    auto conn = vespalib::make_ref_counted<Conn>(_transport->select_thread(nullptr, 0),
                                                 &_dummy_streamer,
                                                 &_dummy_adapter,
                                                 vespalib::SocketHandle(socket(AF_INET, SOCK_STREAM, 0)),
                                                 "dummy_spec");
    _conn_destruct_guard_context = conn->destruct_guard_context();
    _conn = std::move(conn);
    _return_handler = std::make_unique<MyReturnHandler>(_conn_destruct_guard_context, _conn);
}

void
PrepareRestart2RpcHandlerTest::TearDown()
{
    if (_conn_destruct_guard_context) {
        _conn_destruct_guard_context->set_allow_destruct();
    }
}

void
PrepareRestart2RpcHandlerTest::SetUpTestSuite()
{
    _transport = std::make_unique<FNET_Transport>();
    _transport->Start();
}

void
PrepareRestart2RpcHandlerTest::TearDownTestSuite()
{
    _transport->ShutDown(true);
    _transport.reset();
}

std::shared_future<void>
PrepareRestart2RpcHandlerTest::test_handler(uint32_t wait_strategy_id, steady_clock::duration timeout)
{
    auto req_copy = vespalib::ref_counted_from(*_return_handler->_req);
    req_copy->Detach();
    using Handler = DestructionFutureFactory<PrepareRestart2RpcHandler>;
    auto handler = std::make_shared<Handler>(_detached_rpc_requests_owner,
                                             std::move(req_copy),
                                             _notifier,
                                             _transport->GetScheduler(),
                                             wait_strategy_id,
                                             timeout,
                                             _history);
    handler->setup();
    auto future = handler->get_destruction_future();
    handler.reset();
    return future;
}

void
PrepareRestart2RpcHandlerTest::expect_result(bool expect_success)
{
    _return_handler->check_req();
    EXPECT_TRUE(_return_handler->has_detached());
    ASSERT_TRUE(_return_handler->has_returned());
    EXPECT_EQ(expect_success, _return_handler->req_success());
}

void
PrepareRestart2RpcHandlerTest::expect_no_result()
{
    _return_handler->check_req();
    EXPECT_TRUE(_return_handler->has_detached());
    ASSERT_FALSE(_return_handler->has_returned());
}

TEST_F(PrepareRestart2RpcHandlerTest, successful_request)
{
    auto future = test_handler(199, 5s);
    future.wait();
    expect_result(true);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(199, slime.get()["wait_strategy_id"].asLong());
    EXPECT_EQ(200, slime.get()["current"]["id"].asLong());
}

TEST_F(PrepareRestart2RpcHandlerTest, timeout_request)
{
    auto before = steady_clock::now();
    auto future = test_handler(200, 100ms);
    future.wait();
    expect_result(false);
    auto after = steady_clock::now();
    EXPECT_LT(50ms, after - before);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(200, slime.get()["wait_strategy_id"].asLong());
    EXPECT_EQ(200, slime.get()["current"]["id"].asLong());
}

TEST_F(PrepareRestart2RpcHandlerTest, rpc_server_aborted_request)
{
    auto future = test_handler(200, 5s);
    _detached_rpc_requests_owner->close();
    future.wait();
    expect_no_result();
}

TEST_F(PrepareRestart2RpcHandlerTest, missing_rpc_server)
{
    _detached_rpc_requests_owner.reset();
    auto future = test_handler(200, 5s);
    future.wait();
    expect_no_result();
}

TEST_F(PrepareRestart2RpcHandlerTest, notifier_closed)
{
    auto future = test_handler(200, 5s);
    _notifier->close();
    future.wait();
    expect_no_result();
}

TEST_F(PrepareRestart2RpcHandlerTest, missing_notifier)
{
    _notifier.reset();
    auto future = test_handler(200, 5s);
    future.wait();
    expect_no_result();
}

TEST_F(PrepareRestart2RpcHandlerTest, lost_conn)
{
    _conn->Close();
    auto future = test_handler(200, 15s);
    future.wait();
    expect_no_result();
}

TEST_F(PrepareRestart2RpcHandlerTest, missing_wait_strategy_id)
{
    auto future = test_handler(0, 5s);
    future.wait();
    expect_result(false);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(0, slime.get()["wait_strategy_id"].asLong());
    EXPECT_EQ(200, slime.get()["current"]["id"].asLong());
}

TEST_F(PrepareRestart2RpcHandlerTest, missing_wait_strategy_id_and_history)
{
    _history.reset();
    auto future = test_handler(0, 5s);
    future.wait();
    expect_result(false);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(0, slime.get()["wait_strategy_id"].asLong());
    EXPECT_FALSE(slime.get()["current"].valid());
}
