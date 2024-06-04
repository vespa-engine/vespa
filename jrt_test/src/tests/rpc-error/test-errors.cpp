// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/ref_counted.h>

vespalib::string spec;

class TestErrors : public ::testing::Test
{
protected:
    static std::unique_ptr<fnet::frt::StandaloneFRT> server;
    static vespalib::ref_counted<FRT_Target> target;

    vespalib::ref_counted<FRT_RPCRequest> alloc_rpc_request() {
        return vespalib::ref_counted<FRT_RPCRequest>::internal_attach(server->supervisor().AllocRPCRequest());
    }
public:
    TestErrors();
    ~TestErrors() override;

    static void SetUpTestSuite();
    static void TearDownTestSuite();
};

std::unique_ptr<fnet::frt::StandaloneFRT> TestErrors::server;
vespalib::ref_counted<FRT_Target> TestErrors::target;

TestErrors::TestErrors() = default;
TestErrors::~TestErrors() = default;

void
TestErrors::SetUpTestSuite()
{
    server = std::make_unique<fnet::frt::StandaloneFRT>();
    target = vespalib::ref_counted<FRT_Target>::internal_attach(server->supervisor().GetTarget(spec.c_str()));
}

void
TestErrors::TearDownTestSuite()
{
    target.reset();
    server.reset();
}

TEST_F(TestErrors, no_error)
{
    auto req1 = alloc_rpc_request();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(0);
    req1->GetParams()->AddInt8(0);
    target->InvokeSync(req1.get(), 60.0);
    EXPECT_TRUE(!req1->IsError());
    ASSERT_EQ(1, req1->GetReturn()->GetNumValues());
    ASSERT_EQ(42, req1->GetReturn()->GetValue(0)._intval32);
}


TEST_F(TestErrors, no_such_method)
{
    auto req1 = alloc_rpc_request();
    req1->SetMethodName("bogus");
    target->InvokeSync(req1.get(), 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_TRUE(0 == req1->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_NO_SUCH_METHOD == req1->GetErrorCode());
}


TEST_F(TestErrors, wrong_parameters)
{
    auto req1 = alloc_rpc_request();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(0);
    req1->GetParams()->AddInt32(0);
    target->InvokeSync(req1.get(), 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_EQ(0, req1->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_PARAMS == req1->GetErrorCode());
    req1.reset();

    auto req2 = alloc_rpc_request();
    req2->SetMethodName("test");
    req2->GetParams()->AddInt32(42);
    req2->GetParams()->AddInt32(0);
    target->InvokeSync(req2.get(), 60.0);
    EXPECT_TRUE(req2->IsError());
    EXPECT_EQ(0, req2->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_PARAMS == req2->GetErrorCode());
    req2.reset();

    auto req3 = alloc_rpc_request();
    req3->SetMethodName("test");
    req3->GetParams()->AddInt32(42);
    req3->GetParams()->AddInt32(0);
    req3->GetParams()->AddInt8(0);
    req3->GetParams()->AddInt8(0);
    target->InvokeSync(req3.get(), 60.0);
    EXPECT_TRUE(req3->IsError());
    EXPECT_EQ(0, req3->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_PARAMS == req3->GetErrorCode());
}


TEST_F(TestErrors, wrong_return_values)
{
    auto req1 = alloc_rpc_request();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(0);
    req1->GetParams()->AddInt8(1);
    target->InvokeSync(req1.get(), 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_EQ(0, req1->GetReturn()->GetNumValues());
    EXPECT_TRUE(FRTE_RPC_WRONG_RETURN == req1->GetErrorCode());
}


TEST_F(TestErrors, method_failed)
{
    auto req1 = alloc_rpc_request();
    req1->SetMethodName("test");
    req1->GetParams()->AddInt32(42);
    req1->GetParams()->AddInt32(75000);
    req1->GetParams()->AddInt8(0);
    target->InvokeSync(req1.get(), 60.0);
    EXPECT_TRUE(req1->IsError());
    EXPECT_EQ(0, req1->GetReturn()->GetNumValues());
    EXPECT_EQ(75000, req1->GetErrorCode());

    auto req2 = alloc_rpc_request();
    req2->SetMethodName("test");
    req2->GetParams()->AddInt32(42);
    req2->GetParams()->AddInt32(75000);
    req2->GetParams()->AddInt8(1);
    target->InvokeSync(req2.get(), 60.0);
    EXPECT_TRUE(req2->IsError());
    EXPECT_EQ(0, req2->GetReturn()->GetNumValues());
    EXPECT_EQ(75000, req2->GetErrorCode());
}

int
main(int argc, char* argv[])
{
    if (argc != 2) {
        fprintf(stderr, "usage: %s spec\n", argv[0]);
        return 1;
    }
    spec = argv[1];
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
