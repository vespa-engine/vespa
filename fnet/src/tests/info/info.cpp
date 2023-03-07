// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/fnet/channel.h>
#include <vespa/fnet/info.h>
#include <mutex>
#include <condition_variable>
 
struct RPC : public FRT_Invokable
{
  void GetInfo(FRT_RPCRequest *req)
  {
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
    rb.DefineMethod("getInfo", "", "ssii",
                    FRT_METHOD(RPC::GetInfo), this);
    // FNET version
    // endian
    // FD_SETSIZE
    // req object size
    //-------------------------------------------------------------------
  }
};

TEST("info") {
    RPC rpc;
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor & orb = server.supervisor();
    char spec[64];
    rpc.Init(&orb);
    ASSERT_TRUE(orb.Listen("tcp/0"));
    snprintf(spec, sizeof(spec), "tcp/localhost:%d", orb.GetListenPort());

    FRT_Target     *target      = orb.GetTarget(spec);
    FRT_RPCRequest *local_info  = orb.AllocRPCRequest();
    FRT_RPCRequest *remote_info = orb.AllocRPCRequest();

    rpc.GetInfo(local_info);
    remote_info->SetMethodName("getInfo");
    target->InvokeSync(remote_info, 10.0);
    EXPECT_FALSE(remote_info->IsError());

    FRT_Values &l = *local_info->GetReturn();
 // FRT_Values &r = *remote_info->GetReturn();

    fprintf(stderr, "FNET Version: %s\n", l[0]._string._str);
    fprintf(stderr, "Endian: %s\n", l[1]._string._str);
    fprintf(stderr, "FD_SETSIZE: %d\n", l[2]._intval32);
    fprintf(stderr, "sizeof(FRT_RPCRequest): %d\n", l[3]._intval32);

    target->internal_subref();
    local_info->internal_subref();
    remote_info->internal_subref();
};

TEST("size of important objects")
{
#ifdef __APPLE__
    constexpr size_t MUTEX_SIZE = 64u;
#elif defined(__aarch64__)
    constexpr size_t MUTEX_SIZE = 48u;
#else
    constexpr size_t MUTEX_SIZE = 40u;
#endif
    EXPECT_EQUAL(MUTEX_SIZE + sizeof(std::string) + 120u, sizeof(FNET_IOComponent));
    EXPECT_EQUAL(32u, sizeof(FNET_Channel));
    EXPECT_EQUAL(40u, sizeof(FNET_PacketQueue_NoLock));
    EXPECT_EQUAL(MUTEX_SIZE + sizeof(std::string) + 416u, sizeof(FNET_Connection));
    EXPECT_EQUAL(48u, sizeof(std::condition_variable));
    EXPECT_EQUAL(56u, sizeof(FNET_DataBuffer));
    EXPECT_EQUAL(8u, sizeof(FNET_Context));
    EXPECT_EQUAL(MUTEX_SIZE, sizeof(std::mutex));
    EXPECT_EQUAL(MUTEX_SIZE, sizeof(pthread_mutex_t));
    EXPECT_EQUAL(48u, sizeof(pthread_cond_t));
    EXPECT_EQUAL(48u, sizeof(std::condition_variable));
}

TEST_MAIN() { TEST_RUN_ALL(); }
