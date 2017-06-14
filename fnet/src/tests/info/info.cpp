// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/frt.h>
#include <mutex>
#include <condition_variable>
 
struct RPC : public FRT_Invokable
{
  void GetInfo(FRT_RPCRequest *req)
  {
    req->GetReturn()->AddString("fastos X current");
    req->GetReturn()->AddString(FNET_Info::GetFNETVersion());
    const char *endian_str = "UNKNOWN";
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_LITTLE)
      endian_str = "LITTLE";
    if (FNET_Info::GetEndian() == FNET_Info::ENDIAN_BIG)
      endian_str = "BIG";
    req->GetReturn()->AddString(endian_str);
    req->GetReturn()->AddInt32(FD_SETSIZE);
    req->GetReturn()->AddInt32(sizeof(FRT_RPCRequest));
  }

  void Init(FRT_Supervisor *s)
  {
    FRT_ReflectionBuilder rb(s);
    //-------------------------------------------------------------------
    rb.DefineMethod("getInfo", "", "sssii", true,
                    FRT_METHOD(RPC::GetInfo), this);
    // FastOS version
    // FNET version
    // endian
    // FD_SETSIZE
    // req object size
    //-------------------------------------------------------------------
  }
};

TEST("info") {
    RPC rpc;
    FRT_Supervisor orb;
    char spec[64];
    rpc.Init(&orb);
    ASSERT_TRUE(orb.Listen("tcp/0"));
    sprintf(spec, "tcp/localhost:%d", orb.GetListenPort());
    ASSERT_TRUE(orb.Start());

    FRT_Target     *target      = orb.GetTarget(spec);
    FRT_RPCRequest *local_info  = orb.AllocRPCRequest();
    FRT_RPCRequest *remote_info = orb.AllocRPCRequest();

    rpc.GetInfo(local_info);
    remote_info->SetMethodName("getInfo");
    target->InvokeSync(remote_info, 10.0);
    EXPECT_FALSE(remote_info->IsError());

    FRT_Values &l = *local_info->GetReturn();
 // FRT_Values &r = *remote_info->GetReturn();

    fprintf(stderr, "FastOS Version: %s\n", l[0]._string._str);
    fprintf(stderr, "FNET Version: %s\n", l[1]._string._str);
    fprintf(stderr, "Endian: %s\n", l[2]._string._str);
    fprintf(stderr, "FD_SETSIZE: %d\n", l[3]._intval32);
    fprintf(stderr, "sizeof(FRT_RPCRequest): %d\n", l[4]._intval32);

    target->SubRef();
    local_info->SubRef();
    remote_info->SubRef();
    orb.ShutDown(true);
};

TEST("size of important objects")
{
    EXPECT_EQUAL(192u, sizeof(FNET_IOComponent));
    EXPECT_EQUAL(32u, sizeof(FNET_Channel));
    EXPECT_EQUAL(40u, sizeof(FNET_PacketQueue_NoLock));
    EXPECT_EQUAL(480u, sizeof(FNET_Connection));
    EXPECT_EQUAL(96u, sizeof(FastOS_Cond));
    EXPECT_EQUAL(56u, sizeof(FNET_DataBuffer));
    EXPECT_EQUAL(24u, sizeof(FastOS_Time));
    EXPECT_EQUAL(8u, sizeof(FNET_Context));
    EXPECT_EQUAL(8u, sizeof(fastos::TimeStamp));
    EXPECT_EQUAL(48u, sizeof(FastOS_Mutex));
    EXPECT_EQUAL(40u, sizeof(pthread_mutex_t));
    EXPECT_EQUAL(48u, sizeof(pthread_cond_t));
    EXPECT_EQUAL(40u, sizeof(std::mutex));
    EXPECT_EQUAL(48u, sizeof(std::condition_variable));
}

TEST_MAIN() { TEST_RUN_ALL(); }
