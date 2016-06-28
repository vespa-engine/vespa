// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Class used to describe state of disk and memory usage relative to
 * configured limits.
 */
class DiskMemUsageState
{
    bool _aboveDiskLimit;
    bool _aboveMemoryLimit;

public:
    DiskMemUsageState()
        : _aboveDiskLimit(false),
          _aboveMemoryLimit(false)
    {
    }

    DiskMemUsageState(bool aboveDiskLimit_in, bool aboveMemoryLimit_in)
        : _aboveDiskLimit(aboveDiskLimit_in),
          _aboveMemoryLimit(aboveMemoryLimit_in)
    {
    }

    bool operator==(const DiskMemUsageState &rhs) const {
        return ((_aboveDiskLimit == rhs._aboveDiskLimit) &&
                (_aboveMemoryLimit == rhs._aboveMemoryLimit));
    }
    bool operator!=(const DiskMemUsageState &rhs) const {
        return ((_aboveDiskLimit != rhs._aboveDiskLimit) ||
                (_aboveMemoryLimit != rhs._aboveMemoryLimit));
    }
    bool aboveDiskLimit() const { return _aboveDiskLimit; }
    bool aboveMemoryLimit() const { return _aboveMemoryLimit; }
};

} // namespace proton
