// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hw_info.h"
#include <vespa/vespalib/stllike/string.h>

namespace proton {

/*
 * Class detecting some hardware characteristics on the machine, e.g.
 * speed of sequential write to file.
 */
class HwInfoSampler
{
    HwInfo _hwInfo;
public:
    HwInfoSampler(const vespalib::string &path);

    const HwInfo &hwInfo() const { return _hwInfo; }
};

}
