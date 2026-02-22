// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplepacketstreamer.h"

#include "databuffer.h"
#include "ipacketfactory.h"
#include "packet.h"

FNET_SimplePacketStreamer::FNET_SimplePacketStreamer(FNET_IPacketFactory* factory) : _factory(factory) {}

FNET_SimplePacketStreamer::~FNET_SimplePacketStreamer() {}

bool FNET_SimplePacketStreamer::GetPacketInfo(
    FNET_DataBuffer* src, uint32_t* plen, uint32_t* pcode, uint32_t* chid, bool* broken) {
    (void)broken;

    if (src->GetDataLen() < 3 * sizeof(uint32_t))
        return false;

    *plen = src->ReadInt32() - 2 * sizeof(uint32_t);
    *pcode = src->ReadInt32();
    *chid = src->ReadInt32();
    return true;
}

FNET_Packet* FNET_SimplePacketStreamer::Decode(
    FNET_DataBuffer* src, uint32_t plen, uint32_t pcode, FNET_Context context) {
    FNET_Packet* packet;

    packet = _factory->CreatePacket(pcode, context);
    if (packet != nullptr) {
        if (!packet->Decode(src, plen)) {
            packet->Free();
            packet = nullptr;
        }
    } else {
        src->DataToDead(plen);
    }
    src->AssertValid();
    return packet;
}

void FNET_SimplePacketStreamer::Encode(FNET_Packet* packet, uint32_t chid, FNET_DataBuffer* dst) {
    uint32_t len = packet->GetLength();
    uint32_t pcode = packet->GetPCODE();
    dst->EnsureFree(len + 3 * sizeof(uint32_t));
    dst->WriteInt32Fast(len + 2 * sizeof(uint32_t));
    dst->WriteInt32Fast(pcode);
    dst->WriteInt32Fast(chid);
    packet->Encode(dst);
    dst->AssertValid();
}
