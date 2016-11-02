// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "mock_hw_info.h"

namespace vespalib {

MockHwInfo::MockHwInfo()
    : _spinningDisk(false)
{
}

MockHwInfo::MockHwInfo(bool spinningDisk_in)
    : _spinningDisk(spinningDisk_in)
{
}

MockHwInfo::~MockHwInfo()
{
}

bool
MockHwInfo::spinningDisk() const
{
    return _spinningDisk;
}

}
