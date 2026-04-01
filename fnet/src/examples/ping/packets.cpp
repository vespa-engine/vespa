// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packets.h"

#include <vespa/fnet/databuffer.h>

uint32_t PingRequest::GetPCODE() { return PCODE_PING_REQUEST; }

uint32_t PingRequest::GetLength() { return 0; }

void PingRequest::Encode(FNET_DataBuffer*) {}

bool PingRequest::Decode(FNET_DataBuffer* src, uint32_t len) {
    src->DataToDead(len);
    return (len == 0);
}

uint32_t PingReply::GetPCODE() { return PCODE_PING_REPLY; }

uint32_t PingReply::GetLength() { return 0; }

void PingReply::Encode(FNET_DataBuffer*) {}

bool PingReply::Decode(FNET_DataBuffer* src, uint32_t len) {
    src->DataToDead(len);
    return (len == 0);
}

FNET_Packet* PingPacketFactory::CreatePacket(uint32_t pcode, FNET_Context) {
    switch (pcode) {
    case PCODE_PING_REQUEST:
        return new PingRequest();
    case PCODE_PING_REPLY:
        return new PingReply();
    }
    return nullptr;
}
