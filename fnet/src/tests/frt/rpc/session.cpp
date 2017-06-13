// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/frt.h>


class Session
{
private:
  static FastOS_Mutex _lock;
  static int        _cnt;
  int               _val;

public:
  Session() : _val(0)
  {
    _lock.Lock();
    ++_cnt;
    _lock.Unlock();
  }

  ~Session()
  {
    _lock.Lock();
    --_cnt;
    _lock.Unlock();
  }

  void SetValue(int val) { _val = val; }
  int GetValue() const { return _val; }
  static int GetCnt() { return _cnt; }
};

FastOS_Mutex Session::_lock;
int Session::_cnt(0);


struct RPC : public FRT_Invokable
{
  bool bogusFini;

  RPC() : bogusFini(false) {}

  void InitSession(FRT_RPCRequest *req)
  {
    Session *session = new Session();
    req->GetConnection()->SetContext(FNET_Context((void *) session));
  }

  void FiniSession(FRT_RPCRequest *req)
  {
    Session *session =
      (Session *)req->GetConnection()->GetContext()._value.VOIDP;
    bogusFini |= (session == nullptr);
    delete session;
  }

  void GetValue(FRT_RPCRequest *req)
  {
    Session *session =
      (Session *)req->GetConnection()->GetContext()._value.VOIDP;
    req->GetReturn()->AddInt32(session->GetValue());
  }

  void SetValue(FRT_RPCRequest *req)
  {
    Session *session =
      (Session *)req->GetConnection()->GetContext()._value.VOIDP;
    session->SetValue(req->GetParams()->GetValue(0)._intval32);
  }

  void Init(FRT_Supervisor *s)
  {
    FRT_ReflectionBuilder rb(s);
    rb.DefineMethod("getValue", "", "i", true,
                    FRT_METHOD(RPC::GetValue), this);
    rb.DefineMethod("setValue", "i", "", true,
                    FRT_METHOD(RPC::SetValue), this);
    s->SetSessionInitHook(FRT_METHOD(RPC::InitSession), this);
    s->SetSessionFiniHook(FRT_METHOD(RPC::FiniSession), this);
  }
};

TEST("session") {
    RPC rpc;
    FRT_Supervisor orb;
    char spec[64];
    rpc.Init(&orb);
    ASSERT_TRUE(orb.Listen("tcp/0"));
    sprintf(spec, "tcp/localhost:%d", orb.GetListenPort());
    ASSERT_TRUE(orb.Start());

    FRT_Target     *target = orb.GetTarget(spec);
    FRT_RPCRequest *req    = orb.AllocRPCRequest();

    req->SetMethodName("getValue");
    target->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError() &&
                 strcmp(req->GetReturnSpec(), "i") == 0 &&
                 req->GetReturn()->GetValue(0)._intval32 == 0);

    req = orb.AllocRPCRequest(req);
    req->SetMethodName("setValue");
    req->GetParams()->AddInt32(42);
    target->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError() &&
                 strcmp(req->GetReturnSpec(), "") == 0);

    req = orb.AllocRPCRequest(req);
    req->SetMethodName("getValue");
    target->InvokeSync(req, 5.0);
    ASSERT_TRUE(!req->IsError() &&
                 strcmp(req->GetReturnSpec(), "i") == 0 &&
                 req->GetReturn()->GetValue(0)._intval32 == 42);

    EXPECT_TRUE(Session::GetCnt() == 1);

    req->SubRef();
    target->SubRef();
    orb.ShutDown(true);
    EXPECT_TRUE(Session::GetCnt() == 0);
    EXPECT_TRUE(!rpc.bogusFini);
};

TEST_MAIN() { TEST_RUN_ALL(); }
