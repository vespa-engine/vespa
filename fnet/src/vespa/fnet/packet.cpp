// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packet.h"

#include <vespa/vespalib/util/stringfmt.h>

std::string FNET_Packet::Print(uint32_t indent) {
    return vespalib::make_string(
        "%*sFNET_Packet[subclass] { regular=%s, control=%s, "
        "pcode=%d, command=%d, length=%d }\n",
        indent, "", IsRegularPacket() ? "true" : "false", IsControlPacket() ? "true" : "false", GetPCODE(),
        GetCommand(), GetLength());
}
