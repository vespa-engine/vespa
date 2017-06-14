// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_flush_config_updater.h"
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.memory_flush_config_updater");

namespace proton {

namespace {

bool
shouldUseConservativeMode(const ResourceUsageState &resourceState,
                          bool currentlyUseConservativeMode,
                          double lowWatermarkFactor) {
    return resourceState.aboveLimit() ||
           (currentlyUseConservativeMode && resourceState.aboveLimit(lowWatermarkFactor));
}

}

void
MemoryFlushConfigUpdater::considerUseConservativeDiskMode(const LockGuard &,
                                                          MemoryFlush::Config &newConfig)
{
    if (shouldUseConservativeMode(_currState.diskState(), _useConservativeDiskMode,
                                  _currConfig.conservative.lowwatermarkfactor))
    {
        newConfig.maxGlobalTlsSize = _currConfig.maxtlssize * _currConfig.conservative.disklimitfactor;
        _useConservativeDiskMode = true;
    } else {
        _useConservativeDiskMode = false;
    }
}

void
MemoryFlushConfigUpdater::considerUseConservativeMemoryMode(const LockGuard &,
                                                            MemoryFlush::Config &newConfig)
{
    if (shouldUseConservativeMode(_currState.memoryState(), _useConservativeMemoryMode,
                                  _currConfig.conservative.lowwatermarkfactor))
    {
        newConfig.maxGlobalMemory = _currConfig.maxmemory * _currConfig.conservative.memorylimitfactor;
        newConfig.maxMemoryGain = _currConfig.each.maxmemory * _currConfig.conservative.memorylimitfactor;
        _useConservativeMemoryMode = true;
    } else {
        _useConservativeMemoryMode = false;
    }
}

void
MemoryFlushConfigUpdater::updateFlushStrategy(const LockGuard &guard)
{
    MemoryFlush::Config newConfig = convertConfig(_currConfig);
    considerUseConservativeDiskMode(guard, newConfig);
    considerUseConservativeMemoryMode(guard, newConfig);
    _flushStrategy->setConfig(newConfig);
}

MemoryFlushConfigUpdater::MemoryFlushConfigUpdater(const MemoryFlush::SP &flushStrategy,
                                                   const ProtonConfig::Flush::Memory &config)
    : _mutex(),
      _flushStrategy(flushStrategy),
      _currConfig(config),
      _currState(),
      _useConservativeDiskMode(false),
      _useConservativeMemoryMode(false)
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
        LOG(info, "flush.memory.maxmemory=%ld cannot"
            " be set above the hard limit of %ld (16GB) so we cap it to the hard limit",
            config.maxmemory,
            TOTAL_HARD_MEMORY_LIMIT);
        totalMaxMemory = TOTAL_HARD_MEMORY_LIMIT;
    }
    size_t eachMaxMemory = config.each.maxmemory;
    if (eachMaxMemory > EACH_HARD_MEMORY_LIMIT) {
        LOG(info, "flush.memory.each.maxmemory=%ld cannot"
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
                               static_cast<long>
                               (config.maxage.time) *
                               fastos::TimeStamp::NANO);
}

} // namespace proton
