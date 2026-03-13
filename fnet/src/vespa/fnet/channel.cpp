// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "channel.h"

#include "connection.h"

bool FNET_Channel::Send(FNET_Packet* packet) { return _conn->PostPacket(packet, _id); }

void FNET_Channel::Sync() { _conn->Sync(); }

FNET_IPacketHandler::HP_RetCode FNET_Channel::Receive(FNET_Packet* packet) {
    return _handler->HandlePacket(packet, _context);
}

bool FNET_Channel::Close() { return _conn->CloseChannel(this); }

void FNET_Channel::Free() { _conn->FreeChannel(this); }

void FNET_Channel::CloseAndFree() { _conn->CloseAndFreeChannel(this); }
