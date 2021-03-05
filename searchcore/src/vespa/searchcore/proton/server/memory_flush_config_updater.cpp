// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_flush_config_updater.h"
#include <vespa/vespalib/util/size_literals.h>
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
MemoryFlushConfigUpdater::considerUseConservativeDiskMode(const LockGuard &guard,
                                                          MemoryFlush::Config &newConfig)
{
    if (shouldUseConservativeMode(_currState.diskState(), _useConservativeDiskMode,
                                  _currConfig.conservative.lowwatermarkfactor))
    {
        newConfig.maxGlobalTlsSize = _currConfig.maxtlssize * _currConfig.conservative.disklimitfactor;
        _useConservativeDiskMode = true;
    } else {
        _useConservativeDiskMode = false;
        if (_nodeRetired) {
            considerUseRelaxedDiskMode(guard, newConfig);
        }
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
MemoryFlushConfigUpdater::considerUseRelaxedDiskMode(const LockGuard &, MemoryFlush::Config &newConfig)
{
    double utilization = _currState.diskState().utilization();
    double bloatMargin = _currConfig.conservative.lowwatermarkfactor - utilization;
    if (bloatMargin > 0.0) {
        // Node retired and disk utiliation is below low mater mark factor.
        newConfig.diskBloatFactor = 1.0;
        newConfig.globalDiskBloatFactor = std::max(bloatMargin * 0.8, _currConfig.diskbloatfactor);
    }
}

void
MemoryFlushConfigUpdater::updateFlushStrategy(const LockGuard &guard)
{
    MemoryFlush::Config newConfig = convertConfig(_currConfig, _memory);
    considerUseConservativeDiskMode(guard, newConfig);
    considerUseConservativeMemoryMode(guard, newConfig);
    _flushStrategy->setConfig(newConfig);
}

MemoryFlushConfigUpdater::MemoryFlushConfigUpdater(const MemoryFlush::SP &flushStrategy,
                                                   const ProtonConfig::Flush::Memory &config,
                                                   const HwInfo::Memory &memory)
    : _mutex(),
      _flushStrategy(flushStrategy),
      _currConfig(config),
      _memory(memory),
      _currState(),
      _useConservativeDiskMode(false),
      _useConservativeMemoryMode(false),
      _nodeRetired(false)
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

void
MemoryFlushConfigUpdater::setNodeRetired(bool nodeRetired)
{
    LockGuard guard(_mutex);
    _nodeRetired = nodeRetired;
    updateFlushStrategy(guard);
}

namespace {

size_t
getHardMemoryLimit(const HwInfo::Memory &memory)
{
    return memory.sizeBytes() / 4;
}

}

MemoryFlush::Config
MemoryFlushConfigUpdater::convertConfig(const ProtonConfig::Flush::Memory &config,
                                        const HwInfo::Memory &memory)
{
    const size_t hardMemoryLimit = getHardMemoryLimit(memory);
    size_t totalMaxMemory = config.maxmemory;
    if (totalMaxMemory > hardMemoryLimit) {
        LOG(debug, "flush.memory.maxmemory=%" PRId64 " cannot"
            " be set above the hard limit of %ld so we cap it to the hard limit",
            config.maxmemory, hardMemoryLimit);
        totalMaxMemory = hardMemoryLimit;
    }
    size_t eachMaxMemory = config.each.maxmemory;
    if (eachMaxMemory > hardMemoryLimit) {
        LOG(debug, "flush.memory.each.maxmemory=%" PRId64 " cannot"
            " be set above the hard limit of %ld so we cap it to the hard limit",
            config.maxmemory, hardMemoryLimit);
        eachMaxMemory = hardMemoryLimit;
    }
    return MemoryFlush::Config(totalMaxMemory,
                               config.maxtlssize,
                               config.diskbloatfactor,
                               eachMaxMemory,
                               config.each.diskbloatfactor,
                               vespalib::from_s(config.maxage.time));
}

} // namespace proton
