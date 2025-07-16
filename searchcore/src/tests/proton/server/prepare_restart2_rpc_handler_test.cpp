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
using vespalib::Memory;
using vespalib::Slime;
using vespalib::slime::Inspector;
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

const std::string FLUSH_ALL_STRATEGY("flush_all");
const std::string MEMORY_STRATEGY("memory");
const std::string PREPARE_RESTART_STRATEGY("prepare_restart");
const std::string HANDLER1("handler1");
const std::string HANDLER2("handler2");

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
    void expect_strategy(Inspector& inspector, Memory key, const std::string& exp_name, uint32_t exp_id);
    void expect_flush_counts(Inspector& inspector, Memory key, uint32_t exp_flushed, uint32_t exp_flushing,
                             std::optional<uint32_t> exp_pending_flushes);
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

void
PrepareRestart2RpcHandlerTest::expect_strategy(Inspector& inspector, Memory key, const std::string& exp_name,
                                               uint32_t exp_id)
{
    SCOPED_TRACE(key.make_stringview());
    auto& strategy = inspector[key];
    EXPECT_TRUE(strategy.valid());
    EXPECT_EQ(exp_name, strategy["strategy"].asString());
    EXPECT_EQ(exp_id, strategy["id"].asLong());
}

void
PrepareRestart2RpcHandlerTest::expect_flush_counts(Inspector& inspector, Memory key, uint32_t exp_flushed,
                                                   uint32_t exp_flushing, std::optional<uint32_t> exp_pending_flushes)
{
    SCOPED_TRACE(key.make_stringview());
    auto& strategy = inspector[key];
    EXPECT_TRUE(strategy.valid());
    EXPECT_EQ(exp_flushed, strategy["flushed"].asLong());
    EXPECT_EQ(exp_flushing, strategy["flushing"].asLong());
    if (exp_pending_flushes.has_value()) {
        EXPECT_EQ(exp_pending_flushes.value(), strategy["pending_flushes"].asLong());
    } else {
        EXPECT_FALSE(strategy["pending_flushes"].valid());
    }
}

TEST_F(PrepareRestart2RpcHandlerTest, successful_request)
{
    auto future = test_handler(199, 5s);
    future.wait();
    expect_result(true);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(199, slime.get()["wait_strategy_id"].asLong());
    EXPECT_FALSE(slime.get()["previous"].valid());
    expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 200);
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
    EXPECT_FALSE(slime.get()["previous"].valid());
    expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 200);
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
    EXPECT_FALSE(slime.get()["previous"].valid());
    expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 200);
}

TEST_F(PrepareRestart2RpcHandlerTest, missing_wait_strategy_id_and_history)
{
    _history.reset();
    auto future = test_handler(0, 5s);
    future.wait();
    expect_result(false);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(0, slime.get()["wait_strategy_id"].asLong());
    EXPECT_FALSE(slime.get()["previous"].valid());
    EXPECT_FALSE(slime.get()["current"].valid());
}

TEST_F(PrepareRestart2RpcHandlerTest, previous_flush_all)
{
    _history->set_strategy(FLUSH_ALL_STRATEGY, 201, true);
    _history->set_strategy(MEMORY_STRATEGY, 202, false);
    auto future = test_handler(1, 0s);
    future.wait();
    expect_result(true);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(1, slime.get()["wait_strategy_id"].asLong());
    expect_strategy(slime.get(), "previous", FLUSH_ALL_STRATEGY, 201);
    expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 202);
}

TEST_F(PrepareRestart2RpcHandlerTest, previous_flush_all_then_prepare_restart)
{
    _history->set_strategy(FLUSH_ALL_STRATEGY, 201, true);
    _history->set_strategy(MEMORY_STRATEGY, 202, false);
    _history->set_strategy(PREPARE_RESTART_STRATEGY, 203, true);
    _history->set_strategy(MEMORY_STRATEGY, 204, false);
    auto future = test_handler(1, 0s);
    future.wait();
    expect_result(true);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(1, slime.get()["wait_strategy_id"].asLong());
    expect_strategy(slime.get(), "previous", PREPARE_RESTART_STRATEGY, 203);
    expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 204);
}

TEST_F(PrepareRestart2RpcHandlerTest, previous_prepare_restart_then_flush_all)
{
    _history->set_strategy(PREPARE_RESTART_STRATEGY, 201, true);
    _history->set_strategy(MEMORY_STRATEGY, 202, false);
    _history->set_strategy(FLUSH_ALL_STRATEGY, 203, true);
    _history->set_strategy(MEMORY_STRATEGY, 204, false);
    auto future = test_handler(1, 0s);
    future.wait();
    expect_result(true);
    auto& slime = _return_handler->_slime;
    EXPECT_EQ(1, slime.get()["wait_strategy_id"].asLong());
    expect_strategy(slime.get(), "previous", FLUSH_ALL_STRATEGY, 203);
    expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 204);
}

TEST_F(PrepareRestart2RpcHandlerTest, poll_sequence)
{
    /*
     * 2 flush strategies:
     * { "memory", id = 200, priority_strategy = false } started 1 flush: handler1.a1.
     * { "prepare_restart", id = 201, priority_strategy = true  } started 2 flushes: handler2.a2 and handler1.a3
     * and set 1 flush as pending: handler1.a4
     */
    _history->start_flush(HANDLER1, "a1", 3s, 5);
    _history->set_strategy(PREPARE_RESTART_STRATEGY, 201, true);
    _history->add_pending_flush(HANDLER2, "a2", 1s);
    _history->add_pending_flush(HANDLER1, "a3", 4s);
    _history->add_pending_flush(HANDLER1, "a4", 1s);
    _history->start_flush(HANDLER2, "a2", 1s, 6);
    _history->start_flush(HANDLER1, "a3", 4s, 7);

    auto future = test_handler(1, 0s);
    future.wait();
    expect_result(true);
    auto& slime = _return_handler->_slime;
    {
        SCOPED_TRACE("before first completed flush");
        EXPECT_EQ(1, slime.get()["wait_strategy_id"].asLong());
        EXPECT_FALSE(slime.get()["previous"].valid());
        expect_strategy(slime.get(), "current", PREPARE_RESTART_STRATEGY, 201);
        expect_flush_counts(slime.get(), "current", 0, 3, 1);
    }

    /*
     * Complete flush "handler2.a2". Starts pending flush "handler1.a4". Switch to flush trategy
     * { "memory", id = 202, priority_strategy = false }
     */
    _history->flush_done(6);
    _history->prune_done(6);
    _history->start_flush(HANDLER1, "a4", 1s, 8);
    _history->set_strategy(MEMORY_STRATEGY, 202, false);
    _return_handler->alloc_req();
    future = test_handler(1, 0s);
    future.wait();
    expect_result(true);
    {
        SCOPED_TRACE("after first completed flush");
        EXPECT_EQ(1, slime.get()["wait_strategy_id"].asLong());
        expect_strategy(slime.get(), "previous", PREPARE_RESTART_STRATEGY, 201);
        expect_flush_counts(slime.get(), "previous", 1, 3, std::nullopt);
        expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 202);
        expect_flush_counts(slime.get(), "current", 0, 3, 0);
    }

    /*
     * Complete flush "handler1.a1".
     */
    _history->flush_done(5);
    _history->prune_done(5);
    _notifier->set_strategy_id(201);
    _return_handler->alloc_req();
    future = test_handler(1, 0s);
    future.wait();
    expect_result(true);
    {
        SCOPED_TRACE("after second completed flush");
        EXPECT_EQ(1, slime.get()["wait_strategy_id"].asLong());
        expect_strategy(slime.get(), "previous", PREPARE_RESTART_STRATEGY, 201);
        expect_flush_counts(slime.get(), "previous", 2, 2, std::nullopt);
        expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 202);
        expect_flush_counts(slime.get(), "current", 1, 2, 0);
    }

    /*
     * Complete flushes "handler1.a3" and "handler1.a4".
     */
    _history->flush_done(7);
    _history->prune_done(7);
    _history->flush_done(8);
    _history->prune_done(8);
    _notifier->set_strategy_id(202);
    _return_handler->alloc_req();
    future = test_handler(1, 0s);
    future.wait();
    expect_result(true);
    {
        SCOPED_TRACE("after fourth completed flush");
        EXPECT_EQ(1, slime.get()["wait_strategy_id"].asLong());
        expect_strategy(slime.get(), "previous", PREPARE_RESTART_STRATEGY, 201);
        expect_flush_counts(slime.get(), "previous", 4, 0, std::nullopt);
        expect_strategy(slime.get(), "current", MEMORY_STRATEGY, 202);
        expect_flush_counts(slime.get(), "current", 3, 0, 0);
    }
}
