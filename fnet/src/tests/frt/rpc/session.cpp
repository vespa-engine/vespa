// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <mutex>

//-------------------------------------------------------------

#include "my_crypto_engine.hpp"
vespalib::CryptoEngine::SP crypto;

//-------------------------------------------------------------

class Session
{
private:
  static std::mutex _lock;
  static int        _cnt;
  int               _val;

public:
  Session() : _val(0)
  {
      std::lock_guard<std::mutex> guard(_lock);
      ++_cnt;
  }

  ~Session()
  {
      std::lock_guard<std::mutex> guard(_lock);
      --_cnt;
  }

  void SetValue(int val) { _val = val; }
  int GetValue() const { return _val; }
  static int GetCnt() { return _cnt; }
};

std::mutex Session::_lock;
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
    rb.DefineMethod("getValue", "", "i",
                    FRT_METHOD(RPC::GetValue), this);
    rb.DefineMethod("setValue", "i", "",
                    FRT_METHOD(RPC::SetValue), this);
    s->SetSessionInitHook(FRT_METHOD(RPC::InitSession), this);
    s->SetSessionFiniHook(FRT_METHOD(RPC::FiniSession), this);
  }
};

void testSession(RPC & rpc) {
    fnet::frt::StandaloneFRT frt(crypto);
    FRT_Supervisor & orb = frt.supervisor();
    char spec[64];
    rpc.Init(&orb);
    ASSERT_TRUE(orb.Listen("tcp/0"));
    sprintf(spec, "tcp/localhost:%d", orb.GetListenPort());

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
}
TEST("session") {
    RPC rpc;
    testSession(rpc);
    EXPECT_TRUE(Session::GetCnt() == 0);
    EXPECT_TRUE(!rpc.bogusFini);
};

TEST_MAIN() {
    crypto = my_crypto_engine();
    TEST_RUN_ALL();
    crypto.reset();
}
