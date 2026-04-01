// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packets.h"

#include "rpcrequest.h"

#include <vespa/fnet/databuffer.h>
#include <vespa/fnet/info.h>
#include <vespa/vespalib/util/stringfmt.h>

FRT_RPCPacket::~FRT_RPCPacket() {}

void FRT_RPCPacket::Free() {
    if (_ownsRef) {
        _req->DiscardBlobs();
        _req->internal_subref();
    }
}

//--------------------------------------------------------------------

uint32_t FRT_RPCRequestPacket::GetPCODE() { return (_flags << 16) + PCODE_FRT_RPC_REQUEST; }

uint32_t FRT_RPCRequestPacket::GetLength() {
    return (sizeof(uint32_t) + _req->GetMethodNameLen() + _req->GetParams()->GetLength());
}

void FRT_RPCRequestPacket::Encode(FNET_DataBuffer* dst) {
    uint32_t packet_endian =
        ((_flags & FLAG_FRT_RPC_LITTLE_ENDIAN) != 0) ? FNET_Info::ENDIAN_LITTLE : FNET_Info::ENDIAN_BIG;
    uint32_t host_endian = FNET_Info::GetEndian();

    if (packet_endian == host_endian) {
        uint32_t tmp = _req->GetMethodNameLen();
        dst->WriteBytesFast(&tmp, sizeof(tmp));
        dst->WriteBytesFast(_req->GetMethodName(), _req->GetMethodNameLen());
        _req->GetParams()->EncodeCopy(dst);
    } else {
        assert(packet_endian == FNET_Info::ENDIAN_BIG);
        dst->WriteInt32Fast(_req->GetMethodNameLen());
        dst->WriteBytesFast(_req->GetMethodName(), _req->GetMethodNameLen());
        _req->GetParams()->EncodeBig(dst);
    }
}

bool FRT_RPCRequestPacket::Decode(FNET_DataBuffer* src, uint32_t len) {
    uint32_t packet_endian =
        ((_flags & FLAG_FRT_RPC_LITTLE_ENDIAN) != 0) ? FNET_Info::ENDIAN_LITTLE : FNET_Info::ENDIAN_BIG;
    uint32_t host_endian = FNET_Info::GetEndian();
    uint32_t slen;

    if (len < sizeof(uint32_t))
        goto error;
    slen = (packet_endian == FNET_Info::ENDIAN_BIG) ? src->ReadInt32() : src->ReadInt32Reverse();
    len -= sizeof(uint32_t);
    if (len < slen)
        goto error;
    _req->SetMethodName(src->GetData(), slen);
    src->DataToDead(slen);
    len -= slen;

    return (packet_endian == host_endian)
               ? _req->GetParams()->DecodeCopy(src, len)
               : ((packet_endian == FNET_Info::ENDIAN_BIG) ? _req->GetParams()->DecodeBig(src, len)
                                                           : _req->GetParams()->DecodeLittle(src, len));

error:
    src->DataToDead(len);
    return false; // FAIL
}

std::string FRT_RPCRequestPacket::Print(uint32_t indent) {
    std::string s;
    s += vespalib::make_string("%*sFRT_RPCRequestPacket {\n", indent, "");
    s += vespalib::make_string(
        "%*s  method name: %s\n", indent, "", (_req->GetMethodName() != nullptr) ? _req->GetMethodName() : "N/A");
    s += vespalib::make_string("%*s  params:\n", indent, "");
    _req->GetParams()->Print(indent + 2);
    s += vespalib::make_string("%*s}\n", indent, "");
    return s;
}

//--------------------------------------------------------------------

uint32_t FRT_RPCReplyPacket::GetPCODE() { return (_flags << 16) + PCODE_FRT_RPC_REPLY; }

uint32_t FRT_RPCReplyPacket::GetLength() { return _req->GetReturn()->GetLength(); }

void FRT_RPCReplyPacket::Encode(FNET_DataBuffer* dst) {
    uint32_t packet_endian =
        ((_flags & FLAG_FRT_RPC_LITTLE_ENDIAN) != 0) ? FNET_Info::ENDIAN_LITTLE : FNET_Info::ENDIAN_BIG;
    uint32_t host_endian = FNET_Info::GetEndian();

    if (packet_endian == host_endian) {
        _req->GetReturn()->EncodeCopy(dst);
    } else {
        assert(packet_endian == FNET_Info::ENDIAN_BIG);
        _req->GetReturn()->EncodeBig(dst);
    }
}

bool FRT_RPCReplyPacket::Decode(FNET_DataBuffer* src, uint32_t len) {
    uint32_t packet_endian =
        ((_flags & FLAG_FRT_RPC_LITTLE_ENDIAN) != 0) ? FNET_Info::ENDIAN_LITTLE : FNET_Info::ENDIAN_BIG;
    uint32_t host_endian = FNET_Info::GetEndian();

    return (packet_endian == host_endian)
               ? _req->GetReturn()->DecodeCopy(src, len)
               : ((packet_endian == FNET_Info::ENDIAN_BIG) ? _req->GetReturn()->DecodeBig(src, len)
                                                           : _req->GetReturn()->DecodeLittle(src, len));
}

std::string FRT_RPCReplyPacket::Print(uint32_t indent) {
    std::string s;
    s += vespalib::make_string("%*sFRT_RPCReplyPacket {\n", indent, "");
    s += vespalib::make_string("%*s  return:\n", indent, "");
    _req->GetReturn()->Print(indent + 2);
    s += vespalib::make_string("%*s}\n", indent, "");
    return s;
}

//--------------------------------------------------------------------

uint32_t FRT_RPCErrorPacket::GetPCODE() { return (_flags << 16) + PCODE_FRT_RPC_ERROR; }

uint32_t FRT_RPCErrorPacket::GetLength() { return sizeof(uint32_t) * 2 + _req->GetErrorMessageLen(); }

void FRT_RPCErrorPacket::Encode(FNET_DataBuffer* dst) {
    uint32_t packet_endian =
        ((_flags & FLAG_FRT_RPC_LITTLE_ENDIAN) != 0) ? FNET_Info::ENDIAN_LITTLE : FNET_Info::ENDIAN_BIG;
    uint32_t host_endian = FNET_Info::GetEndian();

    if (packet_endian == host_endian) {
        uint32_t tmp = _req->GetErrorCode();
        dst->WriteBytesFast(&tmp, sizeof(tmp));
        tmp = _req->GetErrorMessageLen();
        dst->WriteBytesFast(&tmp, sizeof(tmp));
        dst->WriteBytesFast(_req->GetErrorMessage(), _req->GetErrorMessageLen());
    } else {
        assert(packet_endian == FNET_Info::ENDIAN_BIG);
        dst->WriteInt32Fast(_req->GetErrorCode());
        dst->WriteInt32Fast(_req->GetErrorMessageLen());
        dst->WriteBytesFast(_req->GetErrorMessage(), _req->GetErrorMessageLen());
    }
}

bool FRT_RPCErrorPacket::Decode(FNET_DataBuffer* src, uint32_t len) {
    uint32_t packet_endian =
        ((_flags & FLAG_FRT_RPC_LITTLE_ENDIAN) != 0) ? FNET_Info::ENDIAN_LITTLE : FNET_Info::ENDIAN_BIG;
    uint32_t errorCode;
    uint32_t errorMsgLen;

    if (len < 2 * sizeof(uint32_t))
        goto error;
    errorCode = (packet_endian == FNET_Info::ENDIAN_BIG) ? src->ReadInt32() : src->ReadInt32Reverse();
    errorMsgLen = (packet_endian == FNET_Info::ENDIAN_BIG) ? src->ReadInt32() : src->ReadInt32Reverse();
    len -= 2 * sizeof(uint32_t);
    if (len < errorMsgLen)
        goto error;
    _req->SetError(errorCode, src->GetData(), errorMsgLen);
    src->DataToDead(errorMsgLen);
    len -= errorMsgLen;
    if (len != 0)
        goto error;
    return true; // OK

error:
    src->DataToDead(len);
    return false; // FAIL
}

std::string FRT_RPCErrorPacket::Print(uint32_t indent) {
    std::string s;
    s += vespalib::make_string("%*sFRT_RPCErrorPacket {\n", indent, "");
    s += vespalib::make_string("%*s  error code   : %d\n", indent, "", _req->GetErrorCode());
    s += vespalib::make_string("%*s  error message: %s\n", indent, "",
                               (_req->GetErrorMessage() != nullptr) ? _req->GetErrorMessage() : "N/A");
    s += vespalib::make_string("%*s}\n", indent, "");
    return s;
}

//--------------------------------------------------------------------

FNET_Packet* FRT_PacketFactory::CreatePacket(uint32_t pcode, FNET_Context context) {
    FRT_RPCRequest* req = ((FRT_RPCRequest*)context._value.VOIDP);
    uint32_t        flags = (pcode >> 16) & 0xffff;

    if (req == nullptr || (flags & ~FLAG_FRT_RPC_SUPPORTED_MASK) != 0)
        return nullptr;

    vespalib::Stash& stash = req->getStash();
    pcode &= 0xffff; // remove flags

    switch (pcode) {

    case PCODE_FRT_RPC_REQUEST:
        return &stash.create<FRT_RPCRequestPacket>(req, flags, false);

    case PCODE_FRT_RPC_REPLY:
        return &stash.create<FRT_RPCReplyPacket>(req, flags, false);

    case PCODE_FRT_RPC_ERROR:
        return &stash.create<FRT_RPCErrorPacket>(req, flags, false);
    }
    return nullptr;
}
