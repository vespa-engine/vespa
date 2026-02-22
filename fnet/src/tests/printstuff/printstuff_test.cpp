// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fnet/frt/packets.h>
#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/info.h>
#include <vespa/fnet/packetqueue.h>
#include <vespa/vespalib/gtest/gtest.h>

void printError(uint32_t ecode) {
    fprintf(stderr, "error(%u): %s: %s\n", ecode, FRT_GetErrorCodeName(ecode), FRT_GetDefaultErrorMessage(ecode));
}

TEST(PrintStuffTest, frt_error_code_names_and_default_messages) {
    printError(0);
    printError(99);
    for (uint32_t i = 100; i < 112; ++i) {
        printError(i);
    }
    printError(198);
    printError(199);
    printError(200);
    printError(70000);
}

TEST(PrintStuffTest, rpc_packets_in_a_queue) {
    FRT_RPCRequest* req = new FRT_RPCRequest();
    {
        req->SetMethodName("foo");
        FNET_PacketQueue_NoLock q1(1, FNET_IPacketHandler::FNET_KEEP_CHANNEL);
        q1.QueuePacket_NoLock(&req->getStash().create<FRT_RPCRequestPacket>(req, 0, false), FNET_Context());
        q1.QueuePacket_NoLock(&req->getStash().create<FRT_RPCRequestPacket>(req, 0, false), FNET_Context());
        q1.QueuePacket_NoLock(&req->getStash().create<FRT_RPCRequestPacket>(req, 0, false), FNET_Context());
        q1.Print();
        FNET_PacketQueue q2(2, FNET_IPacketHandler::FNET_KEEP_CHANNEL);
        q2.QueuePacket(&req->getStash().create<FRT_RPCRequestPacket>(req, 0, false), FNET_Context());
        q2.QueuePacket(&req->getStash().create<FRT_RPCRequestPacket>(req, 0, false), FNET_Context());
        q2.QueuePacket(&req->getStash().create<FRT_RPCRequestPacket>(req, 0, false), FNET_Context());
        q2.Print();
    }
    req->internal_subref();
}

TEST(PrintStuffTest, info) {
    FNET_Info::PrintInfo();
    FNET_Info::LogInfo();
}

GTEST_MAIN_RUN_ALL_TESTS()
