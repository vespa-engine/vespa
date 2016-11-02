// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_hw_info.h"

namespace vespalib {

/*
 * Class describing some hardware on the machine.
 */
class HwInfo : public IHwInfo
{
    bool _spinningDisk;
public:
    HwInfo();
    virtual ~HwInfo();
    virtual bool spinningDisk() const override;
};

}
