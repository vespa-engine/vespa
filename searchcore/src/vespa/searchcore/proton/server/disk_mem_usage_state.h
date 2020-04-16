// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resource_usage_state.h"

namespace proton {

/**
 * Class used to describe state of disk and memory usage relative to configured limits.
 */
class DiskMemUsageState
{
    ResourceUsageState _diskState;
    ResourceUsageState _memoryState;

public:
    DiskMemUsageState() = default;
    DiskMemUsageState(const ResourceUsageState &diskState_,
                      const ResourceUsageState &memoryState_)
        : _diskState(diskState_),
          _memoryState(memoryState_)
    {
    }
    bool operator==(const DiskMemUsageState &rhs) const {
        return ((_diskState == rhs._diskState) &&
                (_memoryState == rhs._memoryState));
    }
    bool operator!=(const DiskMemUsageState &rhs) const {
        return ! ((*this) == rhs);
    }
    const ResourceUsageState &diskState() const { return _diskState; }
    const ResourceUsageState &memoryState() const { return _memoryState; }
    bool aboveDiskLimit() const { return diskState().aboveLimit(); }
    bool aboveMemoryLimit() const { return memoryState().aboveLimit(); }
    bool aboveDiskLimit(double resourceLimitFactor) const { return diskState().aboveLimit(resourceLimitFactor); }
    bool aboveMemoryLimit(double resourceLimitFactor) const { return memoryState().aboveLimit(resourceLimitFactor); }
};

} // namespace proton
