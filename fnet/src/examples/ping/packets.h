// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once


enum {
    PCODE_PING_REQUEST = 1,
    PCODE_PING_REPLY   = 2
};


class PingRequest : public FNET_Packet
{
public:
    virtual uint32_t GetPCODE() override;
    virtual uint32_t GetLength() override;
    virtual void Encode(FNET_DataBuffer *) override;
    virtual bool Decode(FNET_DataBuffer *src, uint32_t len) override;
};


class PingReply : public FNET_Packet
{
public:
    virtual uint32_t GetPCODE() override;
    virtual uint32_t GetLength() override;
    virtual void Encode(FNET_DataBuffer *) override;
    virtual bool Decode(FNET_DataBuffer *src, uint32_t len) override;
};


class PingPacketFactory : public FNET_IPacketFactory
{
public:
    virtual FNET_Packet *CreatePacket(uint32_t pcode, FNET_Context) override;
};

