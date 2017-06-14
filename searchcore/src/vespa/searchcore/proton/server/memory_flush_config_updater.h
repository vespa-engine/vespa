// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_disk_mem_usage_listener.h"
#include "memoryflush.h"
#include <vespa/searchcore/config/config-proton.h>
#include <mutex>

namespace proton {

/**
 * Class that listens to changes in disk and memory usage and
 * updates the config used by memory flush strategy accordingly if we reach one of the resource limits.
 */
class MemoryFlushConfigUpdater : public IDiskMemUsageListener
{
private:
    using Mutex = std::mutex;
    using LockGuard = std::lock_guard<Mutex>;
    using ProtonConfig = vespa::config::search::core::ProtonConfig;

    Mutex _mutex;
    MemoryFlush::SP _flushStrategy;
    ProtonConfig::Flush::Memory _currConfig;
    DiskMemUsageState _currState;
    bool _useConservativeDiskMode;
    bool _useConservativeMemoryMode;


    void considerUseConservativeDiskMode(const LockGuard &guard,
                                         MemoryFlush::Config &newConfig);
    void considerUseConservativeMemoryMode(const LockGuard &guard,
                                           MemoryFlush::Config &newConfig);
    void updateFlushStrategy(const LockGuard &guard);

public:
    using UP = std::unique_ptr<MemoryFlushConfigUpdater>;

    MemoryFlushConfigUpdater(const MemoryFlush::SP &flushStrategy,
                             const ProtonConfig::Flush::Memory &config);
    void setConfig(const ProtonConfig::Flush::Memory &newConfig);
    virtual void notifyDiskMemUsage(DiskMemUsageState newState) override;

    static MemoryFlush::Config convertConfig(const ProtonConfig::Flush::Memory &config);
};

} // namespace proton
