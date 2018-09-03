// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/frt.h>
#include <mutex>
#include <condition_variable>

//-------------------------------------------------------------

#include "my_crypto_engine.hpp"
vespalib::CryptoEngine::SP crypto;

//-------------------------------------------------------------

std::mutex   _delayedReturnCntLock;
uint32_t     _delayedReturnCnt = 0;

uint32_t _phase_simple_cnt   = 0;
uint32_t _phase_void_cnt     = 0;
uint32_t _phase_speed_cnt    = 0;
uint32_t _phase_advanced_cnt = 0;
uint32_t _phase_error_cnt    = 0;
uint32_t _phase_timeout_cnt  = 0;
uint32_t _phase_abort_cnt    = 0;
uint32_t _phase_echo_cnt     = 0;

//-------------------------------------------------------------

struct LockedReqWait : public FRT_IRequestWait
{
    std::mutex  _condLock;  // cond used to signal req done
    std::condition_variable _cond;      // cond used to signal req done
    bool        _done;      // flag indicating req done

    std::mutex   _lockLock;  // lock protecting virtual lock
    bool         _lock;      // virtual lock
    bool         _wasLocked; // was 'locked' when req done

    LockedReqWait() : _cond(), _done(false), _lockLock(), _lock(false), _wasLocked(false) {}
    ~LockedReqWait() {}

    void lock() {
        std::lock_guard<std::mutex> guard(_lockLock);
        _lock = true;
    }

    void unlock() {
        std::lock_guard<std::mutex> guard(_lockLock);
        _lock = false;
    }

    bool isLocked() {
        std::lock_guard<std::mutex> guard(_lockLock);
        return _lock;
    }

    void RequestDone(FRT_RPCRequest *) override {
        _wasLocked = isLocked();
        std::lock_guard<std::mutex> guard(_condLock);
        _done = true;
        _cond.notify_one();
    }

    void waitReq() {
        std::unique_lock<std::mutex> guard(_condLock);
        while(!_done) {
            _cond.wait(guard);
        }
    }
};

//-------------------------------------------------------------

class DelayedReturn : public FNET_Task
{
private:
    FRT_RPCRequest *_req;

    DelayedReturn(const DelayedReturn &);
    DelayedReturn &operator=(const DelayedReturn &);

public:
    DelayedReturn(FNET_Scheduler *sched, FRT_RPCRequest *req, double delay)
        : FNET_Task(sched),
          _req(req)
    {
        {
            std::lock_guard<std::mutex> guard(_delayedReturnCntLock);
            _delayedReturnCnt++;
        }
        Schedule(delay);
    }

    void PerformTask() override
    {
        _req->Return();
        std::lock_guard<std::mutex> guard(_delayedReturnCntLock);
        _delayedReturnCnt--;
    }
};

//-------------------------------------------------------------

class EchoTest : public FRT_Invokable
{
private:
    vespalib::Stash *_echo_stash;
    FRT_Values      *_echo_args;

    EchoTest(const EchoTest &);
    EchoTest &operator=(const EchoTest &);

public:
    EchoTest() : _echo_stash(nullptr), _echo_args(nullptr) {}
    ~EchoTest()
    {
        delete _echo_args;
        delete _echo_stash;
    }

    void Init(FRT_Supervisor *supervisor)
    {
        _echo_stash = new vespalib::Stash();
        _echo_args = new FRT_Values(*_echo_stash);
        assert(_echo_stash != nullptr && _echo_args != nullptr);

        FRT_ReflectionBuilder rb(supervisor);
        rb.DefineMethod("echo", "*", "*", true,
                        FRT_METHOD(EchoTest::RPC_Echo), this);

        FRT_Values *args = _echo_args;
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

    bool PrepareEchoReq(FRT_RPCRequest *req)
    {
        FNET_DataBuffer buf;

        req->SetMethodName("echo");
        _echo_args->EncodeCopy(&buf);
        req->GetParams()->DecodeCopy(&buf, buf.GetDataLen());
        return (req->GetParams()->Equals(_echo_args) &&
                _echo_args->Equals(req->GetParams()));
    }

    void RPC_Echo(FRT_RPCRequest *req)
    {
        FNET_DataBuffer buf;

        req->GetParams()->EncodeCopy(&buf);
        req->GetReturn()->DecodeCopy(&buf, buf.GetDataLen());
        if (!req->GetReturn()->Equals(_echo_args) ||
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
    FRT_Supervisor *_supervisor;
    FNET_Scheduler *_scheduler;
    uint32_t        _intValue;

    TestRPC(const TestRPC &);
    TestRPC &operator=(const TestRPC &);

public:
    TestRPC(FRT_Supervisor *supervisor, // server supervisor
            FNET_Scheduler *scheduler)  // client scheduler
        : _supervisor(supervisor),
          _scheduler(scheduler),
          _intValue(0)
    {
        FRT_ReflectionBuilder rb(supervisor);

        rb.DefineMethod("inc", "i", "i", true,
                        FRT_METHOD(TestRPC::RPC_Inc), this);
        rb.DefineMethod("setValue", "i", "", true,
                        FRT_METHOD(TestRPC::RPC_SetValue), this);
        rb.DefineMethod("incValue", "", "", true,
                        FRT_METHOD(TestRPC::RPC_IncValue), this);
        rb.DefineMethod("getValue", "", "i", true,
                        FRT_METHOD(TestRPC::RPC_GetValue), this);
        rb.DefineMethod("testFast", "iiibb", "i", true,
                        FRT_METHOD(TestRPC::RPC_Test), this);
        rb.DefineMethod("testSlow", "iiibb", "i", false,
                        FRT_METHOD(TestRPC::RPC_Test), this);
    }

    void RPC_Test(FRT_RPCRequest *req)
    {
        FRT_Values &param = *req->GetParams();
        uint32_t value = param[0]._intval32;
        uint32_t delay = param[1]._intval32;
        uint32_t error = param[2]._intval32;
        uint8_t  extra = param[3]._intval8;
        uint8_t  async = param[4]._intval8;

        req->GetReturn()->AddInt32(value);
        if (extra != 0) {
            req->GetReturn()->AddInt32(value);
        }
        if (error != 0) {
            req->SetError(error);
        }
        if (async != 0) {
            req->Detach();
            if (delay == 0) {
                req->Return();
            } else {
                req->getStash().create<DelayedReturn>(_scheduler, req, ((double)delay) / 1000.0);
            }
        } else {

            if (delay > 0) {

                const char *suffix      = "testFast";
                uint32_t    suffix_len  = strlen(suffix);
                uint32_t    name_len    = req->GetMethodNameLen();
                bool        remote      = req->GetContext()._value.VOIDP != nullptr;
                bool        instant     = name_len > suffix_len &&
                                          strcmp(req->GetMethodName() + name_len - suffix_len, suffix) == 0;

                if (remote && instant) {

                    // block, but don't cripple server scheduler...
                    // (NB: in 'real life', instant methods should never block)

                    FastOS_TimeInterface *now = _supervisor->GetTransport()->GetTimeSampler();
                    FNET_Scheduler *scheduler = _supervisor->GetScheduler();
                    assert(scheduler->GetTimeSampler() == now);

                    while (delay > 0) {
                        if (delay > 20) {
                            FastOS_Thread::Sleep(20);
                            delay -= 20;
                        } else {
                            FastOS_Thread::Sleep(delay);
                            delay = 0;
                        }
                        now->SetNow();
                        scheduler->CheckTasks();
                    }

                } else {

                    FastOS_Thread::Sleep(delay);
                }
            }
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
};

//-------------------------------------------------------------

enum {
    OK_RET    = 0,
    BOGUS_RET = 1
};

enum {
    PHASE_NULL = 0,
    PHASE_SETUP,
    PHASE_SIMPLE,
    PHASE_VOID,
    PHASE_SPEED,
    PHASE_ADVANCED,
    PHASE_ERROR,
    PHASE_TIMEOUT,
    PHASE_ABORT,
    PHASE_ECHO,
    PHASE_SHUTDOWN,
    PHASE_ZZZ
};

const char phase_names[PHASE_ZZZ][32] =
{
    "nullptr",
    "SETUP",
    "SIMPLE",
    "VOID",
    "SPEED",
    "ADVANCED",
    "ERROR",
    "TIMEOUT",
    "ABORT",
    "ECHO",
    "SHUTDOWN"
};

enum {
    TIMING_NULL = 0,
    TIMING_INSTANT,
    TIMING_NON_INSTANT,
    TIMING_ZZZ
};

const char timing_names[TIMING_ZZZ][32] =
{
    "nullptr",
    "INSTANT",
    "NON-INSTANT"
};

enum {
    HANDLING_NULL = 0,
    HANDLING_SYNC,
    HANDLING_ASYNC,
    HANDLING_ZZZ
};

const char handling_names[HANDLING_ZZZ][32] =
{
    "nullptr",
    "SYNC",
    "ASYNC"
};

//-------------------------------------------------------------

struct State {
    FRT_Supervisor     _client;
    FRT_Supervisor     _server;
    TestRPC            _rpc;
    EchoTest           _echo;
    std::string        _peerSpec;
    uint32_t           _testPhase;
    uint32_t           _timing;
    uint32_t           _handling;
    double             _timeout;
    FRT_Target        *_target;
    FRT_RPCRequest    *_req;

    State()
        : _client(crypto),
          _server(crypto),
          _rpc(&_server, _client.GetScheduler()),
          _echo(),
          _peerSpec(),
          _testPhase(PHASE_NULL),
          _timing(TIMING_NULL),
          _handling(HANDLING_NULL),
          _timeout(5.0),
          _target(nullptr),
          _req(nullptr)
    {
        _client.GetTransport()->SetTCPNoDelay(true);
        _server.GetTransport()->SetTCPNoDelay(true);
        _echo.Init(&_server);
    }

    void SetTimeout(double timeout)
    {
        _timeout = timeout;
    }

    void NewReq()
    {
        if (_req != nullptr) {
            _req->SubRef();
        }
        _req = new FRT_RPCRequest();
    }

    void FreeReq()
    {
        if (_req != nullptr) {
            _req->SubRef();
        }
        _req = nullptr;
    }

    void LostReq()
    {
        _req = nullptr;
    }

    void PrepareTestMethod()
    {
        NewReq();
        bool instant = (_timing == TIMING_INSTANT);
        if (_timing != TIMING_INSTANT &&
            _timing != TIMING_NON_INSTANT)
        {
            ASSERT_TRUE(false); // consult your dealer...
        }
        if (instant) {
            _req->SetMethodName("testFast");
        } else {
            _req->SetMethodName("testSlow");
        }
    }

    void SetTestParams(uint32_t value, uint32_t delay,
                       uint32_t error = FRTE_NO_ERROR,
                       uint8_t  extra = 0)
    {
        _req->GetParams()->AddInt32(value);
        _req->GetParams()->AddInt32(delay);
        _req->GetParams()->AddInt32(error);
        _req->GetParams()->AddInt8(extra);
        bool async = (_handling == HANDLING_ASYNC);
        if (_handling != HANDLING_SYNC &&
            _handling != HANDLING_ASYNC)
        {
            ASSERT_TRUE(false); // consult your dealer...
        }
        _req->GetParams()->AddInt8((async) ? 1 : 0);
    }

    void InvokeSync();
    void InvokeVoid();
    void InvokeAsync(FRT_IRequestWait *w);
    void InvokeTest(uint32_t value,
                    uint32_t delay = 0,
                    uint32_t error = FRTE_NO_ERROR,
                    uint8_t  extra = 0);
    void InvokeTestAndAbort(uint32_t value,
                            uint32_t delay = 0,
                            uint32_t error = FRTE_NO_ERROR,
                            uint8_t  extra = 0);
    bool WaitForDelayedReturnCount(uint32_t wantedCount, double timeout);

private:
    State(const State &);
    State &operator=(const State &);
};


void
State::InvokeSync()
{
    _target->InvokeSync(_req, _timeout);
}


void
State::InvokeVoid()
{
    _target->InvokeVoid(_req);
}


void
State::InvokeAsync(FRT_IRequestWait *w)
{
    _target->InvokeAsync(_req, _timeout, w);
}


void
State::InvokeTest(uint32_t value, uint32_t delay,
                  uint32_t error, uint8_t extra)
{
    PrepareTestMethod();
    SetTestParams(value, delay, error, extra);
    InvokeSync();
}


void
State::InvokeTestAndAbort(uint32_t value, uint32_t delay,
                          uint32_t error, uint8_t extra)
{
    PrepareTestMethod();
    SetTestParams(value, delay, error, extra);
    FRT_SingleReqWait w;
    InvokeAsync(&w);
    _req->Abort();
    w.WaitReq();
}

bool
State::WaitForDelayedReturnCount(uint32_t wantedCount, double timeout)
{
    FastOS_Time timer;
    timer.SetNow();
    for (;;) {
        uint32_t delayedReturnCnt;
        {
            std::lock_guard<std::mutex> guard(_delayedReturnCntLock);
            delayedReturnCnt = _delayedReturnCnt;
        }
        if (delayedReturnCnt == wantedCount) {
            return true;
        }
        if ((timer.MilliSecsToNow() / 1000.0) > timeout) {
            return false;
        }
        FastOS_Thread::Sleep(10);
    }
}

//-------------------------------------------------------------

bool CheckTypes(FRT_RPCRequest *req, const char *spec) {
    return FRT_Values::CheckTypes(spec, req->GetReturnSpec());
}

FRT_Value &Get(FRT_RPCRequest *req, uint32_t idx) {
    return req->GetReturn()->GetValue(idx);
}

//-------------------------------------------------------------

void TestSetup(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_SETUP);

    bool listenOK      = _state->_server.Listen("tcp/0");

    char spec[64];
    sprintf(spec, "tcp/localhost:%d", _state->_server.GetListenPort());
    _state->_peerSpec = spec;

    bool serverStartOK = _state->_server.Start();
    bool clientStartOK = _state->_client.Start();

    ASSERT_TRUE(listenOK);
    ASSERT_TRUE(serverStartOK);
    ASSERT_TRUE(clientStartOK);

    _state->_target = _state->_client.GetTarget(_state->_peerSpec.c_str());
    _state->NewReq();
    _state->_req->SetMethodName("frt.rpc.ping");
    _state->_target->InvokeSync(_state->_req, 5.0);
    ASSERT_TRUE(!_state->_req->IsError());
}


void TestSimple(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_SIMPLE);
    _phase_simple_cnt++;
    _state->NewReq();
    _state->_req->SetMethodName("inc");
    _state->_req->GetParams()->AddInt32(502);
    _state->InvokeSync();
    EXPECT_TRUE(!_state->_req->IsError() &&
                CheckTypes(_state->_req, "i") &&
                Get(_state->_req, 0)._intval32 == 503);
}


void TestVoid(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_VOID);
    _phase_void_cnt++;

    _state->NewReq();
    _state->_req->SetMethodName("setValue");
    _state->_req->GetParams()->AddInt32(40);
    _state->InvokeSync();
    EXPECT_TRUE(!_state->_req->IsError() &&
                CheckTypes(_state->_req, ""));

    _state->NewReq();
    _state->_req->SetMethodName("incValue");
    _state->InvokeVoid();
    _state->LostReq();

    _state->NewReq();
    _state->_req->SetMethodName("incValue");
    _state->InvokeVoid();
    _state->LostReq();

    _state->NewReq();
    _state->_req->SetMethodName("getValue");
    _state->InvokeSync();
    EXPECT_TRUE(!_state->_req->IsError() &&
                CheckTypes(_state->_req, "i") &&
                Get(_state->_req, 0)._intval32 == 42);
}


void TestSpeed(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_SPEED);
    _phase_speed_cnt++;

    FastOS_Time start;
    FastOS_Time stop;
    uint32_t    val = 0;
    uint32_t    cnt = 0;

    _state->NewReq();
    FRT_RPCRequest *req    = _state->_req;
    FRT_Target     *target = _state->_target;

    // calibrate cnt to be used
    start.SetNow();
    for (cnt = 0; cnt < 1000000; cnt++) {
        req->SetMethodName("inc");
        req->GetParams()->AddInt32(0);
        target->InvokeSync(req, 5.0);
        if (req->IsError()) {
            break;
        }
        req->Reset(); // ok if no error
        if (start.MilliSecsToNow() > 20.0) {
            break;
        }
    }
    cnt = (cnt == 0) ? 1 : cnt * 10;

    fprintf(stderr, "checking invocation latency... (cnt = %d)\n", cnt);

    _state->NewReq();
    req = _state->_req;

    // actual benchmark
    start.SetNow();
    for (uint32_t i = 0; i < cnt; i++) {
        req->SetMethodName("inc");
        req->GetParams()->AddInt32(val);
        target->InvokeSync(req, 60.0);
        if (req->IsError()) {
            fprintf(stderr, "... rpc error(%d): %s\n",
                    req->GetErrorCode(),
                    req->GetErrorMessage());
            break;
        }
        val = req->GetReturn()->GetValue(0)._intval32;
        req->Reset(); // ok if no error
    }
    stop.SetNow();
    stop -= start;
    double latency = stop.MilliSecs() / (double) cnt;

    EXPECT_EQUAL(val, cnt);
    fprintf(stderr, "latency of invocation: %1.3f ms\n", latency);
}


void TestAdvanced(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_ADVANCED);
    _phase_advanced_cnt++;

    // Test invocation
    //----------------
    _state->InvokeTest(42);
    EXPECT_TRUE(!_state->_req->IsError() &&
                CheckTypes(_state->_req, "i") &&
                Get(_state->_req, 0)._intval32 == 42);

    // Abort has no effect after request is done
    //------------------------------------------
    _state->_req->Abort();
    EXPECT_TRUE(!_state->_req->IsError() &&
                CheckTypes(_state->_req, "i") &&
                Get(_state->_req, 0)._intval32 == 42);

    // Test invocation with delay
    //---------------------------
    _state->InvokeTest(58, 100);
    EXPECT_TRUE(!_state->_req->IsError() &&
                CheckTypes(_state->_req, "i") &&
                Get(_state->_req, 0)._intval32 == 58);
}


void TestError(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_ERROR);
    _phase_error_cnt++;

    // bad target -> sync error -> avoid deadlock
    //-------------------------------------------
    if (_state->_handling == HANDLING_ASYNC)
    {
        // stash away valid target
        FRT_Target *stateTarget = _state->_target; // backup of valid target

        _state->_target = _state->_client.GetTarget("bogus address");
        _state->NewReq();
        _state->_req->SetMethodName("frt.rpc.ping");
        LockedReqWait lw;
        lw.lock();
        _state->InvokeAsync(&lw);
        lw.unlock();
        lw.waitReq();
        EXPECT_TRUE(!lw._wasLocked);
        EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_CONNECTION);

        // restore valid target
        _state->_target->SubRef();
        _state->_target = stateTarget;
    }

    // no such method
    //---------------
    if (_state->_timing == TIMING_INSTANT &&
        _state->_handling == HANDLING_SYNC)
    {
        _state->NewReq();
        _state->_req->SetMethodName("bogus");
        _state->InvokeSync();
        EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_NO_SUCH_METHOD);
    }

    // wrong params
    //-------------
    if (_state->_handling == HANDLING_SYNC) {

        _state->PrepareTestMethod();
        _state->InvokeSync();
        EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_WRONG_PARAMS);

        _state->PrepareTestMethod();
        _state->_req->GetParams()->AddInt32(42);
        _state->_req->GetParams()->AddInt32(0);
        _state->_req->GetParams()->AddInt8(0);
        _state->_req->GetParams()->AddInt8(0);
        _state->_req->GetParams()->AddInt8(0);
        _state->InvokeSync();
        EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_WRONG_PARAMS);

        _state->PrepareTestMethod();
        _state->_req->GetParams()->AddInt32(42);
        _state->_req->GetParams()->AddInt32(0);
        _state->_req->GetParams()->AddInt32(0);
        _state->_req->GetParams()->AddInt8(0);
        _state->_req->GetParams()->AddInt8(0);
        _state->_req->GetParams()->AddInt8(0);
        _state->InvokeSync();
        EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_WRONG_PARAMS);
    }

    // wrong return
    //-------------
    _state->InvokeTest(42, 0, 0, BOGUS_RET);
    EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_WRONG_RETURN);

    // method failed
    //--------------
    _state->InvokeTest(42, 0, 5000, BOGUS_RET);
    EXPECT_TRUE(_state->_req->GetErrorCode() == 5000);
}


void TestTimeout(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_TIMEOUT);
    _phase_timeout_cnt++;

    _state->SetTimeout(0.1);

    // Test timeout
    //-------------
    _state->InvokeTest(123, 5000);
    EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_TIMEOUT);
    FastOS_Thread::Sleep(5500); // settle

    _state->SetTimeout(5.0);
}


void TestAbort(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_ABORT);
    _phase_abort_cnt++;

    // Test abort
    //-----------
    _state->InvokeTestAndAbort(456, 1000);
    EXPECT_TRUE(_state->_req->GetErrorCode() == FRTE_RPC_ABORT);
    FastOS_Thread::Sleep(1500); // settle
}


void TestEcho(State *_state) {
    ASSERT_TRUE(_state->_testPhase == PHASE_ECHO);
    _phase_echo_cnt++;

    // Test echo
    //----------
    _state->NewReq();
    EXPECT_TRUE(_state->_echo.PrepareEchoReq(_state->_req));
    _state->InvokeSync();
    EXPECT_TRUE(!_state->_req->IsError());
    EXPECT_TRUE(_state->_req->GetReturn()->Equals(_state->_req->GetParams()));
}


TEST_F("invoke test", State()) {
    State *_state = &f1;

    _state->_testPhase = PHASE_SETUP;
    TestSetup(_state);

    for (_state->_testPhase = PHASE_SIMPLE;
         _state->_testPhase < PHASE_SHUTDOWN;
         _state->_testPhase++) {

        {
            for (_state->_timing = TIMING_INSTANT;
                 _state->_timing < TIMING_ZZZ;
                 _state->_timing++) {

                for (_state->_handling = HANDLING_SYNC;
                     _state->_handling < HANDLING_ZZZ;
                     _state->_handling++) {

                    switch (_state->_testPhase) {
                    case PHASE_SIMPLE:
                        if (_state->_timing   == TIMING_INSTANT &&
                            _state->_handling == HANDLING_SYNC)
                        {
                            TestSimple(_state);
                        }
                        break;
                    case PHASE_VOID:
                        if (_state->_timing     == TIMING_INSTANT &&
                            _state->_handling   == HANDLING_SYNC)
                        {
                            TestVoid(_state);
                        }
                        break;
                    case PHASE_SPEED:
                        if (_state->_timing     == TIMING_INSTANT &&
                            _state->_handling   == HANDLING_SYNC)
                        {
                            TestSpeed(_state);
                        }
                        break;
                    case PHASE_ADVANCED:
                        TestAdvanced(_state);
                        break;
                    case PHASE_ERROR:
                        TestError(_state);
                        break;
                    case PHASE_TIMEOUT:
                        TestTimeout(_state);
                        break;
                    case PHASE_ABORT:
                        TestAbort(_state);
                        break;
                    case PHASE_ECHO:
                        if (_state->_timing     == TIMING_INSTANT &&
                            _state->_handling   == HANDLING_SYNC)
                        {
                            TestEcho(_state);
                        }
                        break;
                    default:
                        ASSERT_TRUE(false); // consult your dealer...
                    }
                }
            }
        }
    }
    _state->_testPhase  = PHASE_SHUTDOWN;
    _state->_timing     = TIMING_NULL;
    _state->_handling   = HANDLING_NULL;
    EXPECT_TRUE(_state->WaitForDelayedReturnCount(0, 120.0));
    _state->FreeReq();
    _state->_client.ShutDown(true);
    _state->_server.ShutDown(true);
    _state->_target->SubRef();
    _state->_target = nullptr;
    EXPECT_TRUE(_delayedReturnCnt == 0);
    EXPECT_TRUE(_phase_simple_cnt == 1);
    EXPECT_TRUE(_phase_void_cnt == 1);
    EXPECT_TRUE(_phase_speed_cnt == 1);
    EXPECT_TRUE(_phase_advanced_cnt == 4);
    EXPECT_TRUE(_phase_error_cnt == 4);
    EXPECT_TRUE(_phase_abort_cnt == 4);
    EXPECT_TRUE(_phase_echo_cnt == 1);
}

TEST_MAIN() {
    crypto = my_crypto_engine();
    TEST_RUN_ALL();
}
