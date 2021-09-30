// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/latch.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/invoker.h>
#include <mutex>
#include <condition_variable>

using vespalib::SocketSpec;
using vespalib::BenchmarkTimer;

constexpr double timeout = 60.0;
constexpr double short_timeout = 0.1;

//-------------------------------------------------------------

#include "my_crypto_engine.hpp"
vespalib::CryptoEngine::SP crypto;

//-------------------------------------------------------------

class RequestLatch : public FRT_IRequestWait {
private:
    vespalib::Latch<FRT_RPCRequest*> _latch;
public:
    RequestLatch() : _latch() {}
    ~RequestLatch() override { ASSERT_TRUE(!has_req()); }
    bool has_req() { return _latch.has_value(); }
    FRT_RPCRequest *read() { return _latch.read(); }
    void write(FRT_RPCRequest *req) { _latch.write(req); }
    void RequestDone(FRT_RPCRequest *req) override { write(req); }
};

//-------------------------------------------------------------

class MyReq {
private:
    FRT_RPCRequest *_req;
public:
    explicit MyReq(FRT_RPCRequest *req) : _req(req) {}
    explicit MyReq(const char *method_name)
        : _req(new FRT_RPCRequest())
    {
        _req->SetMethodName(method_name);
    }
    MyReq(uint32_t value, bool async, uint32_t error, uint8_t extra)
        : _req(new FRT_RPCRequest())
    {
        _req->SetMethodName("test");
        _req->GetParams()->AddInt32(value);
        _req->GetParams()->AddInt32(error);
        _req->GetParams()->AddInt8(extra);
        _req->GetParams()->AddInt8((async) ? 1 : 0);
    }
    ~MyReq() {
        if (_req != nullptr) {
            _req->SubRef();
        }
    }
    MyReq(const MyReq &rhs) = delete;
    MyReq &operator=(const MyReq &rhs) = delete;
    FRT_RPCRequest &get() { return *_req; }
    FRT_RPCRequest *borrow() { return _req; }
    FRT_RPCRequest *steal() {
        auto ret = _req;
        _req = nullptr;
        return ret;
    }
    uint32_t get_int_ret() {
        ASSERT_TRUE(_req != nullptr);
        ASSERT_TRUE(_req->CheckReturnTypes("i"));
        return _req->GetReturn()->GetValue(0)._intval32;
    }
};

//-------------------------------------------------------------

class EchoTest : public FRT_Invokable
{
private:
    vespalib::Stash _echo_stash;
    FRT_Values      _echo_args;

    EchoTest(const EchoTest &);
    EchoTest &operator=(const EchoTest &);

public:
    EchoTest(FRT_Supervisor *supervisor)
        : _echo_stash(),
          _echo_args(_echo_stash)
    {
        FRT_ReflectionBuilder rb(supervisor);
        rb.DefineMethod("echo", "*", "*",
                        FRT_METHOD(EchoTest::RPC_Echo), this);

        FRT_Values *args = &_echo_args;
        args->EnsureFree(16);

        args->AddInt8(8);
        uint8_t *pt_int8 = args->AddInt8Array(3);
        pt_int8[0] = 1;
        pt_int8[1] = 2;
        pt_int8[2] = 3;

        args->AddInt16(16);
        uint16_t *pt_int16 = args->AddInt16Array(3);
        pt_int16[0] = 2;
        pt_int16[1] = 4;
        pt_int16[2] = 6;

        args->AddInt32(32);
        uint32_t *pt_int32 = args->AddInt32Array(3);
        pt_int32[0] = 4;
        pt_int32[1] = 8;
        pt_int32[2] = 12;

        args->AddInt64(64);
        uint64_t *pt_int64 = args->AddInt64Array(3);
        pt_int64[0] = 8;
        pt_int64[1] = 16;
        pt_int64[2] = 24;

        args->AddFloat(32.5);
        float *pt_float = args->AddFloatArray(3);
        pt_float[0] = 0.25;
        pt_float[1] = 0.5;
        pt_float[2] = 0.75;

        args->AddDouble(64.5);
        double *pt_double = args->AddDoubleArray(3);
        pt_double[0] = 0.1;
        pt_double[1] = 0.2;
        pt_double[2] = 0.3;

        args->AddString("string");
        FRT_StringValue *pt_string = args->AddStringArray(3);
        args->SetString(&pt_string[0], "str1");
        args->SetString(&pt_string[1], "str2");
        args->SetString(&pt_string[2], "str3");

        args->AddData("data", 4);
        FRT_DataValue *pt_data = args->AddDataArray(3);
        args->SetData(&pt_data[0], "dat1", 4);
        args->SetData(&pt_data[1], "dat2", 4);
        args->SetData(&pt_data[2], "dat3", 4);
    }

    bool prepare_params(FRT_RPCRequest &req)
    {
        FNET_DataBuffer buf;

        _echo_args.EncodeCopy(&buf);
        req.GetParams()->DecodeCopy(&buf, buf.GetDataLen());
        return (req.GetParams()->Equals(&_echo_args) &&
                _echo_args.Equals(req.GetParams()));
    }

    void RPC_Echo(FRT_RPCRequest *req)
    {
        FNET_DataBuffer buf;

        req->GetParams()->EncodeCopy(&buf);
        req->GetReturn()->DecodeCopy(&buf, buf.GetDataLen());
        if (!req->GetReturn()->Equals(&_echo_args) ||
            !req->GetReturn()->Equals(req->GetParams()))
        {
            req->SetError(10000, "Streaming error");
        }
    }
};

//-------------------------------------------------------------

class TestRPC : public FRT_Invokable
{
private:
    uint32_t        _intValue;
    RequestLatch    _detached_req;

    TestRPC(const TestRPC &);
    TestRPC &operator=(const TestRPC &);

public:
    TestRPC(FRT_Supervisor *supervisor)
        : _intValue(0),
          _detached_req()
    {
        FRT_ReflectionBuilder rb(supervisor);

        rb.DefineMethod("inc", "i", "i",
                        FRT_METHOD(TestRPC::RPC_Inc), this);
        rb.DefineMethod("setValue", "i", "",
                        FRT_METHOD(TestRPC::RPC_SetValue), this);
        rb.DefineMethod("incValue", "", "",
                        FRT_METHOD(TestRPC::RPC_IncValue), this);
        rb.DefineMethod("getValue", "", "i",
                        FRT_METHOD(TestRPC::RPC_GetValue), this);
        rb.DefineMethod("test", "iibb", "i",
                        FRT_METHOD(TestRPC::RPC_Test), this);
    }

    void RPC_Test(FRT_RPCRequest *req)
    {
        FRT_Values &param = *req->GetParams();
        uint32_t value = param[0]._intval32;
        uint32_t error = param[1]._intval32;
        uint8_t  extra = param[2]._intval8;
        uint8_t  async = param[3]._intval8;

        req->GetReturn()->AddInt32(value);
        if (extra != 0) {
            req->GetReturn()->AddInt32(value);
        }
        if (error != 0) {
            req->SetError(error);
        }
        if (async != 0) {
            _detached_req.write(req->Detach());
        }
    }

    void RPC_Inc(FRT_RPCRequest *req)
    {
        req->GetReturn()->AddInt32(req->GetParams()->GetValue(0)._intval32 + 1);
    }

    void RPC_SetValue(FRT_RPCRequest *req)
    {
        _intValue = req->GetParams()->GetValue(0)._intval32;
    }

    void RPC_IncValue(FRT_RPCRequest *req)
    {
        (void) req;
        _intValue++;
    }

    void RPC_GetValue(FRT_RPCRequest *req)
    {
        req->GetReturn()->AddInt32(_intValue);
    }

    RequestLatch &detached_req() { return _detached_req; }
};

//-------------------------------------------------------------

class Fixture
{
private:
    fnet::frt::StandaloneFRT  _client;
    fnet::frt::StandaloneFRT  _server;
    vespalib::string   _peerSpec;
    FRT_Target        *_target;
    TestRPC            _testRPC;
    EchoTest           _echoTest;

public:
    FRT_Target &target() { return *_target; }
    FRT_Target *make_bad_target() { return _client.supervisor().GetTarget("bogus address"); }
    RequestLatch &detached_req() { return _testRPC.detached_req(); }
    EchoTest &echo() { return _echoTest; }

    Fixture()
        : _client(crypto),
          _server(crypto),
          _peerSpec(),
          _target(nullptr),
          _testRPC(&_server.supervisor()),
          _echoTest(&_server.supervisor())
    {
        ASSERT_TRUE(_server.supervisor().Listen("tcp/0"));
        _peerSpec = SocketSpec::from_host_port("localhost", _server.supervisor().GetListenPort()).spec();
        _target = _client.supervisor().GetTarget(_peerSpec.c_str());
        //---------------------------------------------------------------------
        MyReq req("frt.rpc.ping");
        target().InvokeSync(req.borrow(), timeout);
        ASSERT_TRUE(!req.get().IsError());
    }

    ~Fixture() {
        _target->SubRef();
    }
};

//-------------------------------------------------------------

TEST_F("require that simple invocation works", Fixture()) {
    MyReq req("inc");
    req.get().GetParams()->AddInt32(502);
    f1.target().InvokeSync(req.borrow(), timeout);
    EXPECT_EQUAL(req.get_int_ret(), 503u);
}

TEST_F("require that void invocation works", Fixture()) {
    {
        MyReq req("setValue");
        req.get().GetParams()->AddInt32(40);
        f1.target().InvokeSync(req.borrow(), timeout);
        EXPECT_TRUE(req.get().CheckReturnTypes(""));
    }
    {
        MyReq req("incValue");
        f1.target().InvokeVoid(req.steal());
    }
    {
        MyReq req("incValue");
        f1.target().InvokeVoid(req.steal());
    }
    {
        MyReq req("getValue");
        f1.target().InvokeSync(req.borrow(), timeout);
        EXPECT_EQUAL(req.get_int_ret(), 42u);
    }
}

TEST_F("measure minimal invocation latency", Fixture()) {
    size_t cnt = 0;
    uint32_t val = 0;
    BenchmarkTimer timer(1.0);
    while (timer.has_budget()) {
        timer.before();
        {
            MyReq req("inc");
            req.get().GetParams()->AddInt32(val);
            f1.target().InvokeSync(req.borrow(), timeout);
            ASSERT_TRUE(!req.get().IsError());
            val = req.get_int_ret();
            ++cnt;
        }
        timer.after();
    }
    EXPECT_EQUAL(cnt, val);
    double t = timer.min_time();
    fprintf(stderr, "latency of invocation: %1.3f ms\n", t * 1000.0);
}

TEST_F("require that abort has no effect on a completed request", Fixture()) {
    MyReq req(42, false, FRTE_NO_ERROR, 0);
    f1.target().InvokeSync(req.borrow(), timeout);
    EXPECT_EQUAL(req.get_int_ret(), 42u);
    req.get().Abort();
    EXPECT_EQUAL(req.get_int_ret(), 42u);
}

TEST_F("require that a request can be responded to at a later time", Fixture()) {
    RequestLatch result;
    MyReq req(42, true, FRTE_NO_ERROR, 0);
    f1.target().InvokeAsync(req.steal(), timeout, &result);
    EXPECT_TRUE(!result.has_req());
    f1.detached_req().read()->Return();
    MyReq ret(result.read());
    EXPECT_EQUAL(ret.get_int_ret(), 42u);
}

TEST_F("require that a bad target gives connection error", Fixture()) {
    MyReq req("frt.rpc.ping");
    {
        FRT_Target *bad_target = f1.make_bad_target();
        bad_target->InvokeSync(req.borrow(), timeout);
        bad_target->SubRef();
    }
    EXPECT_EQUAL(req.get().GetErrorCode(), FRTE_RPC_CONNECTION);
}

TEST_F("require that non-existing method gives appropriate error", Fixture()) {
    MyReq req("bogus");
    f1.target().InvokeSync(req.borrow(), timeout);
    EXPECT_EQUAL(req.get().GetErrorCode(), FRTE_RPC_NO_SUCH_METHOD);    
}

TEST_F("require that wrong parameter types give appropriate error", Fixture()) {
    MyReq req("setValue");
    req.get().GetParams()->AddString("40");
    f1.target().InvokeSync(req.borrow(), timeout);
    EXPECT_EQUAL(req.get().GetErrorCode(), FRTE_RPC_WRONG_PARAMS);
}

TEST_F("require that wrong return value types give appropriate error", Fixture()) {
    MyReq req(42, false, FRTE_NO_ERROR, 1);
    f1.target().InvokeSync(req.borrow(), timeout);
    EXPECT_EQUAL(req.get().GetErrorCode(), FRTE_RPC_WRONG_RETURN);
}

TEST_F("require that the method itself can signal failure", Fixture()) {
    MyReq req(42, false, 5000, 1);
    f1.target().InvokeSync(req.borrow(), timeout);
    EXPECT_EQUAL(req.get().GetErrorCode(), 5000u);
}

TEST_F("require that invocation can time out", Fixture()) {
    RequestLatch result;
    MyReq req(42, true, FRTE_NO_ERROR, 0);
    f1.target().InvokeAsync(req.steal(), short_timeout, &result);
    MyReq ret(result.read());
    f1.detached_req().read()->Return();
    EXPECT_EQUAL(ret.get().GetErrorCode(), FRTE_RPC_TIMEOUT);
}

TEST_F("require that invocation can be aborted", Fixture()) {
    RequestLatch result;
    MyReq req(42, true, FRTE_NO_ERROR, 0);
    FRT_RPCRequest *will_be_mine_again_soon = req.steal();
    f1.target().InvokeAsync(will_be_mine_again_soon, timeout, &result);
    will_be_mine_again_soon->Abort();
    MyReq ret(result.read());
    f1.detached_req().read()->Return();
    EXPECT_EQUAL(ret.get().GetErrorCode(), FRTE_RPC_ABORT);
}

TEST_F("require that parameters can be echoed as return values", Fixture()) {
    MyReq req("echo");
    ASSERT_TRUE(f1.echo().prepare_params(req.get()));
    f1.target().InvokeSync(req.borrow(), timeout);
    EXPECT_TRUE(!req.get().IsError());
    EXPECT_TRUE(req.get().GetReturn()->Equals(req.get().GetParams()));
    EXPECT_TRUE(req.get().GetParams()->Equals(req.get().GetReturn()));
}

TEST_MAIN() {
    crypto = my_crypto_engine();
    TEST_RUN_ALL();
    crypto.reset();
}
