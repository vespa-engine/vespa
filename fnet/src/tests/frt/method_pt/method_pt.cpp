// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
class Test;
class SimpleHandler;
class MediumHandler;
class ComplexHandler;

//-------------------------------------------------------------

Test             *_test;

std::unique_ptr<fnet::frt::StandaloneFRT> _server;
FRT_Supervisor   *_supervisor;
FRT_Target       *_target;
SimpleHandler    *_simpleHandler;
MediumHandler    *_mediumHandler;
ComplexHandler   *_complexHandler;

bool              _mediumHandlerOK;
bool              _complexHandlerOK;

//-------------------------------------------------------------

class MediumA
{
public:

  /**
   * Destructor.  No cleanup needed for base class.
   */
  virtual ~MediumA(void) { }

  virtual void foo() = 0;
};


class MediumB
{
public:

  /**
   * Destructor.  No cleanup needed for base class.
   */
  virtual ~MediumB(void) { }

  virtual void bar() = 0;
};

//-------------------------------------------------------------

#ifdef __clang__
#define UNUSED_MEMBER [[maybe_unused]]
#else
#define UNUSED_MEMBER
#endif

class ComplexA
{
private:
  UNUSED_MEMBER uint32_t _fill1;
  UNUSED_MEMBER uint32_t _fill2;
  UNUSED_MEMBER uint32_t _fill3;

public:

  /**
   * Destructor.  No cleanup needed for base class.
   */
  virtual ~ComplexA(void) { }

  ComplexA() : _fill1(1), _fill2(2), _fill3(3) {}
  virtual void foo() {}
};


class ComplexB
{
private:
  UNUSED_MEMBER uint32_t _fill1;
  UNUSED_MEMBER uint32_t _fill2;
  UNUSED_MEMBER uint32_t _fill3;

public:

  /**
   * Destructor.  No cleanup needed for base class.
   */
  virtual ~ComplexB(void) { }

  ComplexB() : _fill1(1), _fill2(2), _fill3(3) {}
  virtual void bar() {}
};

//-------------------------------------------------------------

class SimpleHandler : public FRT_Invokable
{
public:
  void RPC_Method(FRT_RPCRequest *req);
};

//-------------------------------------------------------------

class MediumHandler : public FRT_Invokable,
                      public MediumA,
                      public MediumB
{
public:
    void foo() override {}
    void bar() override {}
    void RPC_Method(FRT_RPCRequest *req);
};

//-------------------------------------------------------------

class ComplexHandler : public FRT_Invokable,
                       public ComplexA,
                       public ComplexB
{
public:
    void foo() override {}
    void bar() override {}
    void RPC_Method(FRT_RPCRequest *req);
};

//-------------------------------------------------------------

void initTest() {
    _server = std::make_unique<fnet::frt::StandaloneFRT>();
  _supervisor      = &_server->supervisor();
  _simpleHandler   = new SimpleHandler();
  _mediumHandler   = new MediumHandler();
  _complexHandler  = new ComplexHandler();

  ASSERT_TRUE(_supervisor != nullptr);
  ASSERT_TRUE(_simpleHandler != nullptr);
  ASSERT_TRUE(_mediumHandler != nullptr);
  ASSERT_TRUE(_complexHandler != nullptr);

  ASSERT_TRUE(_supervisor->Listen(0));
  std::string spec = vespalib::make_string("tcp/localhost:%d",
                                           _supervisor->GetListenPort());
  _target = _supervisor->GetTarget(spec.c_str());
  ASSERT_TRUE(_target != nullptr);

  FRT_ReflectionBuilder rb(_supervisor);

  //-------------------------------------------------------------------

  rb.DefineMethod("simpleMethod", "", "",
                  FRT_METHOD(SimpleHandler::RPC_Method),
                  _simpleHandler);

  //-------------------------------------------------------------------

  rb.DefineMethod("mediumMethod", "", "",
                  FRT_METHOD(MediumHandler::RPC_Method),
                  _mediumHandler);

  //-------------------------------------------------------------------

  rb.DefineMethod("complexMethod", "", "",
                  FRT_METHOD(ComplexHandler::RPC_Method),
                  _complexHandler);

  //-------------------------------------------------------------------

  _mediumHandlerOK  = true;
  _complexHandlerOK = true;
}


void finiTest() {
  delete _complexHandler;
  delete _mediumHandler;
  delete _simpleHandler;
  _target->SubRef();
  _server.reset();
}


TEST("method pt") {
  FRT_RPCRequest *req = _supervisor->AllocRPCRequest();
  req->SetMethodName("simpleMethod");
  _target->InvokeSync(req, 60.0);
  EXPECT_TRUE(!req->IsError());

  //-------------------------------- MEDIUM

  req->SubRef();
  req = _supervisor->AllocRPCRequest();
  req->SetMethodName("mediumMethod");
  _target->InvokeSync(req, 60.0);
  EXPECT_TRUE(!req->IsError());

  //-------------------------------- COMPLEX

  req->SubRef();
  req = _supervisor->AllocRPCRequest();
  req->SetMethodName("complexMethod");
  _target->InvokeSync(req, 60.0);
  EXPECT_TRUE(!req->IsError());

  if (_mediumHandlerOK) {
      fprintf(stderr, "Interface inheritance OK for method handlers\n");
  } else {
      fprintf(stderr, "Interface inheritance NOT ok for method handlers\n");
  }

  if (_complexHandlerOK) {
      fprintf(stderr, "Object inheritance OK for method handlers\n");
  } else {
      fprintf(stderr, "Object inheritance NOT ok for method handlers\n");
  }

  req->SubRef();
}

//-------------------------------------------------------------

void
SimpleHandler::RPC_Method(FRT_RPCRequest *req)
{
  (void) req;
  EXPECT_TRUE(this == _simpleHandler);
}

//-------------------------------------------------------------

void
MediumHandler::RPC_Method(FRT_RPCRequest *req)
{
  (void) req;
  _mediumHandlerOK = (_mediumHandlerOK &&
                      this == _mediumHandler);
}

//-------------------------------------------------------------

void
ComplexHandler::RPC_Method(FRT_RPCRequest *req)
{
  (void) req;
  _complexHandlerOK = (_complexHandlerOK &&
                       this == _complexHandler);
}

//-------------------------------------------------------------

TEST_MAIN() {
    initTest();
    TEST_RUN_ALL();
    finiTest();
}
