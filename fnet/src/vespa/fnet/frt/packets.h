// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fnet/ipacketfactory.h>
#include <vespa/fnet/packet.h>
#include <vespa/vespalib/util/traits.h>

class FRT_RPCRequest;

enum {
    PCODE_FRT_RPC_FIRST = 100,
    PCODE_FRT_RPC_REQUEST = 100,
    PCODE_FRT_RPC_REPLY = 101,
    PCODE_FRT_RPC_ERROR = 102,
    PCODE_FRT_RPC_LAST = 199
};

enum { FLAG_FRT_RPC_LITTLE_ENDIAN = 0x0001, FLAG_FRT_RPC_NOREPLY = 0x0002, FLAG_FRT_RPC_SUPPORTED_MASK = 0x0003 };

class FRT_RPCPacket : public FNET_Packet {
protected:
    FRT_RPCRequest* _req;
    uint32_t        _flags;
    bool            _ownsRef;

    FRT_RPCPacket(const FRT_RPCPacket&);
    FRT_RPCPacket& operator=(const FRT_RPCPacket&);

public:
    FRT_RPCPacket(FRT_RPCRequest* req, uint32_t flags, bool ownsRef) : _req(req), _flags(flags), _ownsRef(ownsRef) {}

    bool LittleEndian() { return (_flags & FLAG_FRT_RPC_LITTLE_ENDIAN) != 0; }
    bool NoReply() { return (_flags & FLAG_FRT_RPC_NOREPLY) != 0; }

    ~FRT_RPCPacket();
    void Free() override;
};

class FRT_RPCRequestPacket : public FRT_RPCPacket {
public:
    FRT_RPCRequestPacket(FRT_RPCRequest* req, uint32_t flags, bool ownsRef) : FRT_RPCPacket(req, flags, ownsRef) {}

    uint32_t    GetPCODE() override;
    uint32_t    GetLength() override;
    void        Encode(FNET_DataBuffer* dst) override;
    bool        Decode(FNET_DataBuffer* src, uint32_t len) override;
    std::string Print(uint32_t indent = 0) override;
};

class FRT_RPCReplyPacket : public FRT_RPCPacket {
public:
    FRT_RPCReplyPacket(FRT_RPCRequest* req, uint32_t flags, bool ownsRef) : FRT_RPCPacket(req, flags, ownsRef) {}

    uint32_t    GetPCODE() override;
    uint32_t    GetLength() override;
    void        Encode(FNET_DataBuffer* dst) override;
    bool        Decode(FNET_DataBuffer* src, uint32_t len) override;
    std::string Print(uint32_t indent = 0) override;
};

class FRT_RPCErrorPacket : public FRT_RPCPacket {
public:
    FRT_RPCErrorPacket(FRT_RPCRequest* req, uint32_t flags, bool ownsRef) : FRT_RPCPacket(req, flags, ownsRef) {}

    uint32_t    GetPCODE() override;
    uint32_t    GetLength() override;
    void        Encode(FNET_DataBuffer* dst) override;
    bool        Decode(FNET_DataBuffer* src, uint32_t len) override;
    std::string Print(uint32_t indent = 0) override;
};

class FRT_PacketFactory : public FNET_IPacketFactory {
public:
    FNET_Packet* CreatePacket(uint32_t pcode, FNET_Context context) override;
};

VESPA_CAN_SKIP_DESTRUCTION(FRT_RPCRequestPacket)
VESPA_CAN_SKIP_DESTRUCTION(FRT_RPCReplyPacket)
VESPA_CAN_SKIP_DESTRUCTION(FRT_RPCErrorPacket)
