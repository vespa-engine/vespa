// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "info.h"

#include <vespa/vespalib/component/vtag.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

uint32_t  FNET_Info::_endian = FNET_Info::ENDIAN_UNKNOWN;
FNET_Info global_fnet_info_object; // Global object used to call FNET_Info constructor once at program startup.

FNET_Info::FNET_Info() {
    uint8_t* pt = nullptr;
    uint64_t cmp = 0;
    uint32_t endian = ENDIAN_UNKNOWN;

    uint16_t intval16;
    uint32_t intval32;
    uint64_t intval64;

    pt = (uint8_t*)&intval16;
    pt[0] = 1;
    pt[1] = 2;

    pt = (uint8_t*)&intval32;
    pt[0] = 1;
    pt[1] = 2;
    pt[2] = 3;
    pt[3] = 4;

    pt = (uint8_t*)&intval64;
    pt[0] = 1;
    pt[1] = 2;
    pt[2] = 3;
    pt[3] = 4;
    pt[4] = 5;
    pt[5] = 6;
    pt[6] = 7;
    pt[7] = 8;

    cmp = 0x08070605;
    cmp = (cmp << 32) + 0x04030201;
    if (intval16 == 0x0201 && intval32 == 0x04030201 && intval64 == cmp)
        endian = ENDIAN_LITTLE;

    cmp = 0x01020304;
    cmp = (cmp << 32) + 0x05060708;
    if (intval16 == 0x0102 && intval32 == 0x01020304 && intval64 == cmp)
        endian = ENDIAN_BIG;

    _endian = endian;
}

const char* FNET_Info::GetFNETVersion() { return vespalib::VersionTag; }

void FNET_Info::PrintInfo() {
    printf("This method is deprecated. "
           "Use the FNET_Info::LogInfo method instead.\n");
}

void FNET_Info::LogInfo() {
    LOG(info, "FNET Version    : %s", GetFNETVersion());
    const char* endian_str = "UNKNOWN";
    if (_endian == ENDIAN_LITTLE)
        endian_str = "LITTLE";
    if (_endian == ENDIAN_BIG)
        endian_str = "BIG";
    LOG(info, "Host Endian     : %s", endian_str);
    const char* thread_str = HasThreads() ? "true" : "false";
    LOG(info, "Thread support  : %s", thread_str);
}
