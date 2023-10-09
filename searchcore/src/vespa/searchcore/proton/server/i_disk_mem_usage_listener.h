// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "disk_mem_usage_state.h"

namespace proton {

/**
 * Interface used to receive notification when disk/memory usage state
 * has changed.
 */
class IDiskMemUsageListener
{
public:
    virtual ~IDiskMemUsageListener() {}
    virtual void notifyDiskMemUsage(DiskMemUsageState state) = 0;
};

} // namespace proton
