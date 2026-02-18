// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "controlpacket.h"

#include "context.h"

#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet.controlpacket");

void FNET_ControlPacket::Free() {}

bool FNET_ControlPacket::IsRegularPacket() { return false; }

bool FNET_ControlPacket::IsControlPacket() { return true; }

uint32_t FNET_ControlPacket::GetCommand() { return _command; }

bool FNET_ControlPacket::IsChannelLostCMD() { return _command == FNET_CMD_CHANNEL_LOST; }

bool FNET_ControlPacket::IsTimeoutCMD() { return _command == FNET_CMD_TIMEOUT; }

bool FNET_ControlPacket::IsBadPacketCMD() { return _command == FNET_CMD_BAD_PACKET; }

uint32_t FNET_ControlPacket::GetPCODE() { return FNET_NOID; }

uint32_t FNET_ControlPacket::GetLength() { return 0; }

void FNET_ControlPacket::Encode(FNET_DataBuffer*) { LOG_ABORT("should not be reached"); }

bool FNET_ControlPacket::Decode(FNET_DataBuffer*, uint32_t) { LOG_ABORT("should not be reached"); }

std::string FNET_ControlPacket::Print(uint32_t indent) {
    return vespalib::make_string("%*sFNET_ControlPacket { command = %d }\n", indent, "", _command);
}

FNET_ControlPacket FNET_ControlPacket::ChannelLost(FNET_CMD_CHANNEL_LOST);

FNET_ControlPacket FNET_ControlPacket::IOCAdd(FNET_CMD_IOC_ADD);

FNET_ControlPacket FNET_ControlPacket::IOCEnableWrite(FNET_CMD_IOC_ENABLE_WRITE);

FNET_ControlPacket FNET_ControlPacket::IOCHandshakeACT(FNET_CMD_IOC_HANDSHAKE_ACT);

FNET_ControlPacket FNET_ControlPacket::IOCClose(FNET_CMD_IOC_CLOSE);

FNET_ControlPacket FNET_ControlPacket::DetachServerAdapterInit(FNET_CMD_DETACH_SERVER_ADAPTER_INIT);

FNET_ControlPacket FNET_ControlPacket::DetachServerAdapterFini(FNET_CMD_DETACH_SERVER_ADAPTER_FINI);

FNET_ControlPacket FNET_ControlPacket::Execute(FNET_CMD_EXECUTE);

FNET_ControlPacket FNET_ControlPacket::Timeout(FNET_CMD_TIMEOUT);

FNET_ControlPacket FNET_ControlPacket::BadPacket(FNET_CMD_BAD_PACKET);
