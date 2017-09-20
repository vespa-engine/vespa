// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/*
 * Class describing some hardware on the machine.
 */
class HwInfo
{
    bool _slowDisk;
public:
    HwInfo()
        : _slowDisk(false)
    {
    }

    HwInfo(bool slowDisk_in)
        : _slowDisk(slowDisk_in)
    {
    }

    bool slowDisk() const { return _slowDisk; }
};

}
