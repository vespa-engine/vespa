// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_resource_usage_listener.h"
#include "memoryflush.h"
#include <vespa/config-proton.h>
#include <vespa/vespalib/util/hw_info.h>
#include <mutex>

namespace proton {

/**
 * Class that listens to changes in disk and memory usage and
 * updates the config used by memory flush strategy accordingly if we reach one of the resource limits.
 */
class MemoryFlushConfigUpdater : public IResourceUsageListener
{
private:
    using Mutex = std::mutex;
    using LockGuard = std::lock_guard<Mutex>;
    using ProtonConfig = vespa::config::search::core::ProtonConfig;

    Mutex                       _mutex;
    MemoryFlush::SP             _flushStrategy;
    ProtonConfig::Flush::Memory _currConfig;
    vespalib::HwInfo::Memory    _memory;
    ResourceUsageState          _currState;
    bool                        _useConservativeDiskMode;
    bool                        _useConservativeMemoryMode;
    bool                        _node_retired_or_maintenance;


    void considerUseConservativeDiskMode(const LockGuard &guard, MemoryFlush::Config &newConfig);
    void considerUseConservativeMemoryMode(const LockGuard &guard, MemoryFlush::Config &newConfig);
    void considerUseRelaxedDiskMode(const LockGuard &guard, MemoryFlush::Config &newConfig);
    void updateFlushStrategy(const LockGuard &guard, const char * why);

public:
    using UP = std::unique_ptr<MemoryFlushConfigUpdater>;

    MemoryFlushConfigUpdater(const MemoryFlush::SP &flushStrategy,
                             const ProtonConfig::Flush::Memory &config,
                             const vespalib::HwInfo::Memory &memory);
    void setConfig(const ProtonConfig::Flush::Memory &newConfig);
    void set_node_retired_or_maintenance(bool value);
    void notify_resource_usage(const ResourceUsageState& newState) override;

    static MemoryFlush::Config convertConfig(const ProtonConfig::Flush::Memory &config,
                                             const vespalib::HwInfo::Memory &memory);
};

} // namespace proton
