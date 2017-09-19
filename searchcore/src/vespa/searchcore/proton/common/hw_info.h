// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace proton {

/*
 * Class describing some hardware on the machine.
 */
class HwInfo
{
    uint64_t _diskSizeBytes;
    bool _slowDisk;
    uint64_t _memorySizeBytes;

public:
    HwInfo()
        : _diskSizeBytes(0),
          _slowDisk(false),
          _memorySizeBytes(0)
    {
    }

    HwInfo(uint64_t diskSizeBytes_in,
           bool slowDisk_in,
           uint64_t memorySizeBytes_in)
        : _diskSizeBytes(diskSizeBytes_in),
          _slowDisk(slowDisk_in),
          _memorySizeBytes(memorySizeBytes_in)
    {
    }

    uint64_t diskSizeBytes() const { return _diskSizeBytes; }
    bool slowDisk() const { return _slowDisk; }
    uint64_t memorySizeBytes() const { return _memorySizeBytes; }
};

}
