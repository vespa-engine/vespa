// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.memory_flush_config_updater");
#include "memory_flush_config_updater.h"

namespace proton {

void
MemoryFlushConfigUpdater::updateFlushStrategy(const LockGuard &)
{
    MemoryFlush::Config newConfig = convertConfig(_currConfig);
    if (_currState.aboveDiskLimit()) {
        newConfig.maxGlobalTlsSize = _currConfig.conservative.maxtlssize;
    }
    if (_currState.aboveMemoryLimit()) {
        newConfig.maxGlobalMemory = _currConfig.conservative.maxmemory;
    }
    _flushStrategy->setConfig(newConfig);
}

MemoryFlushConfigUpdater::MemoryFlushConfigUpdater(const MemoryFlush::SP &flushStrategy,
                                                   const ProtonConfig::Flush::Memory &config)
    : _flushStrategy(flushStrategy),
      _currConfig(config),
      _currState()
{
}

void
MemoryFlushConfigUpdater::setConfig(const ProtonConfig::Flush::Memory &newConfig)
{
    LockGuard guard(_mutex);
    _currConfig = newConfig;
    updateFlushStrategy(guard);
}

void
MemoryFlushConfigUpdater::notifyDiskMemUsage(DiskMemUsageState newState)
{
    LockGuard guard(_mutex);
    _currState = newState;
    updateFlushStrategy(guard);
}

namespace {

static constexpr size_t TOTAL_HARD_MEMORY_LIMIT = 16 * 1024 * 1024 * 1024ul;
static constexpr size_t EACH_HARD_MEMORY_LIMIT = 12 * 1024 * 1024 * 1024ul;

}

MemoryFlush::Config
MemoryFlushConfigUpdater::convertConfig(const ProtonConfig::Flush::Memory &config)
{
    size_t totalMaxMemory = config.maxmemory;
    if (totalMaxMemory > TOTAL_HARD_MEMORY_LIMIT) {
        LOG(warning, "flush.memory.maxmemory=%ld cannot"
            " be set above the hard limit of %ld (16GB) so we cap it to the hard limit",
            config.maxmemory,
            TOTAL_HARD_MEMORY_LIMIT);
        totalMaxMemory = TOTAL_HARD_MEMORY_LIMIT;
    }
    size_t eachMaxMemory = config.each.maxmemory;
    if (eachMaxMemory > EACH_HARD_MEMORY_LIMIT) {
        LOG(warning, "flush.memory.each.maxmemory=%ld cannot"
            " be set above the hard limit of %ld (12GB) so we cap it to the hard limit",
            config.maxmemory,
            EACH_HARD_MEMORY_LIMIT);
        eachMaxMemory = EACH_HARD_MEMORY_LIMIT;
    }
    return MemoryFlush::Config(totalMaxMemory,
                               config.maxtlssize,
                               config.diskbloatfactor,
                               eachMaxMemory,
                               config.each.diskbloatfactor,
                               config.maxage.serial,
                               static_cast<long>
                               (config.maxage.time) *
                               fastos::TimeStamp::NANO);
}

} // namespace proton
