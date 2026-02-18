// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/ipacketfactory.h>
#include <vespa/fnet/packet.h>

enum { PCODE_PING_REQUEST = 1, PCODE_PING_REPLY = 2 };

class PingRequest : public FNET_Packet {
public:
    uint32_t GetPCODE() override;
    uint32_t GetLength() override;
    void     Encode(FNET_DataBuffer*) override;
    bool     Decode(FNET_DataBuffer* src, uint32_t len) override;
};

class PingReply : public FNET_Packet {
public:
    uint32_t GetPCODE() override;
    uint32_t GetLength() override;
    void     Encode(FNET_DataBuffer*) override;
    bool     Decode(FNET_DataBuffer* src, uint32_t len) override;
};

class PingPacketFactory : public FNET_IPacketFactory {
public:
    FNET_Packet* CreatePacket(uint32_t pcode, FNET_Context) override;
};
