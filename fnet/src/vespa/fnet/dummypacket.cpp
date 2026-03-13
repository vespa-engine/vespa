// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummypacket.h"

#include "context.h"

#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet.controlpacket");

FNET_DummyPacket::FNET_DummyPacket() {}

bool FNET_DummyPacket::IsRegularPacket() { return false; }

bool FNET_DummyPacket::IsControlPacket() { return false; }

uint32_t FNET_DummyPacket::GetPCODE() { return FNET_NOID; }

uint32_t FNET_DummyPacket::GetLength() { return 0; }

void FNET_DummyPacket::Encode(FNET_DataBuffer*) { LOG_ABORT("should not be reached"); }

bool FNET_DummyPacket::Decode(FNET_DataBuffer*, uint32_t) { LOG_ABORT("should not be reached"); }

std::string FNET_DummyPacket::Print(uint32_t indent) {
    return vespalib::make_string("%*sFNET_DummyPacket {}\n", indent, "");
}
