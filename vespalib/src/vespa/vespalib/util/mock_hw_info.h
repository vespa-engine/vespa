// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_hw_info.h"

namespace vespalib {

/*
 * Mockup class describing some hardware on the machine.  Used by unit testing.
 */
class MockHwInfo : public IHwInfo
{
    bool _spinningDisk;
public:
    MockHwInfo();
    MockHwInfo(bool spinningDisk_in);
    virtual ~MockHwInfo();
    virtual bool spinningDisk() const override;
};

}
