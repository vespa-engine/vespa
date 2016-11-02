// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "hw_info.h"
#include <mutex>
#include <stdlib.h>

namespace vespalib {

namespace
{

std::once_flag setupFlag;
bool spinningDisk_detected = false;

void setupHwInfo()
{
    char *envSpinningDisk = getenv("VESPA_HW_INFO_SPINNING_DISK");
    if (envSpinningDisk != nullptr) {
        spinningDisk_detected = true;
    }
}

}

HwInfo::HwInfo()
    : _spinningDisk(false)
{
    std::call_once(setupFlag, [](){ setupHwInfo(); });
    _spinningDisk = spinningDisk_detected;
}

HwInfo::~HwInfo()
{
}

bool
HwInfo::spinningDisk() const
{
    return _spinningDisk;
}

}
