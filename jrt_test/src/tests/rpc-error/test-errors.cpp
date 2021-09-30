// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>

class TestErrors : public vespalib::TestApp
{
private:
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor *client;
    FRT_Target     *target;

public:
    void init(const char *spec) {
	    client = & server.supervisor();
        target = client->GetTarget(spec);
    }

    void fini() {
	    target->SubRef();
        target = nullptr;
        client = nullptr;
    }

    void testNoError();
    void testNoSuchMethod();
    void testWrongParameters();
    void testWrongReturnValues();
    void testMethodFailed();
    int Main() override;
};


void
TestErrors::testNoError()
{
    FRT_RPCRequest *req1 = client->AllocRPCRequest();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(0);
    req1->GetParams()->AddInt8(0);
    target->InvokeSync(req1, 60.0);
    EXPECT_TRUE(!req1->IsError());
    if (EXPECT_TRUE(1 == req1->GetReturn()->GetNumValues())) {
        EXPECT_TRUE(42 == req1->GetReturn()->GetValue(0)._intval32);
    } else {
        EXPECT_TRUE(false);
    }
    req1->SubRef();
}


void
TestErrors::testNoSuchMethod()
{
    FRT_RPCRequest *req1 = client->AllocRPCRequest();
    req1->SetMethodName("bogus");
    target->InvokeSync(req1, 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_TRUE(0 == req1->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_NO_SUCH_METHOD == req1->GetErrorCode());
    req1->SubRef();
}


void
TestErrors::testWrongParameters()
{
    FRT_RPCRequest *req1 = client->AllocRPCRequest();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(0);
    req1->GetParams()->AddInt32(0);
    target->InvokeSync(req1, 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_TRUE(0 == req1->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_PARAMS == req1->GetErrorCode());
    req1->SubRef();

    FRT_RPCRequest *req2 = client->AllocRPCRequest();
    req2->SetMethodName("test");
    req2->GetParams()->AddInt32(42);
    req2->GetParams()->AddInt32(0);
    target->InvokeSync(req2, 60.0);
    EXPECT_TRUE(req2->IsError());
    EXPECT_TRUE(0 == req2->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_PARAMS == req2->GetErrorCode());
    req2->SubRef();

    FRT_RPCRequest *req3 = client->AllocRPCRequest();
    req3->SetMethodName("test");
    req3->GetParams()->AddInt32(42);
    req3->GetParams()->AddInt32(0);
    req3->GetParams()->AddInt8(0);
    req3->GetParams()->AddInt8(0);
    target->InvokeSync(req3, 60.0);
    EXPECT_TRUE(req3->IsError());
    EXPECT_TRUE(0 == req3->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_PARAMS == req3->GetErrorCode());
    req3->SubRef();
}


void
TestErrors::testWrongReturnValues()
{
    FRT_RPCRequest *req1 = client->AllocRPCRequest();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(0);
    req1->GetParams()->AddInt8(1);
    target->InvokeSync(req1, 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_TRUE(0 == req1->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_RETURN == req1->GetErrorCode());	
    req1->SubRef();
}


void
TestErrors::testMethodFailed()
{
    FRT_RPCRequest *req1 = client->AllocRPCRequest();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(75000);
    req1->GetParams()->AddInt8(0);
    target->InvokeSync(req1, 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_TRUE(0 == req1->GetReturn()->GetNumValues());
    EXPECT_TRUE(75000 == req1->GetErrorCode());	
    req1->SubRef();

    FRT_RPCRequest *req2 = client->AllocRPCRequest();
    req2->SetMethodName("test");
    req2->GetParams()->AddInt32(42);
    req2->GetParams()->AddInt32(75000);
    req2->GetParams()->AddInt8(1);
    target->InvokeSync(req2, 60.0);
    EXPECT_TRUE(req2->IsError());
    EXPECT_TRUE(0 == req2->GetReturn()->GetNumValues());
    EXPECT_TRUE(75000 == req2->GetErrorCode());	
    req2->SubRef();
}


int
TestErrors::Main()
{
    if (_argc != 2) {
        fprintf(stderr, "usage: %s spec", _argv[0]);
        return 1;
    }
    TEST_INIT("test_errors");
    init(_argv[1]);
    testNoError();
    testNoSuchMethod();
    testWrongParameters();
    testWrongReturnValues();
    testMethodFailed();
    fini();
    TEST_DONE();
}


TEST_APPHOOK(TestErrors);
