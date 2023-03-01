// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_flush_config_updater.h"
#include <vespa/vespalib/util/size_literals.h>
#include <cinttypes>
#include <vespa/log/log.h>

LOG_SETUP(".proton.server.memory_flush_config_updater");

namespace proton {

namespace {

bool
shouldUseConservativeMode(const ResourceUsageState &resourceState,
                          bool currentlyUseConservativeMode,
                          double high_watermark_factor,
                          double lowWatermarkFactor)
{
    return resourceState.aboveLimit(high_watermark_factor) ||
           (currentlyUseConservativeMode && resourceState.aboveLimit(lowWatermarkFactor));
}

}

void
MemoryFlushConfigUpdater::considerUseConservativeDiskMode(const LockGuard &guard, MemoryFlush::Config &newConfig)
{
    if (shouldUseConservativeMode(_currState.diskState(), _useConservativeDiskMode,
                                  _currConfig.conservative.highwatermarkfactor,
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
MemoryFlushConfigUpdater::considerUseConservativeMemoryMode(const LockGuard &, MemoryFlush::Config &newConfig)
{
    if (shouldUseConservativeMode(_currState.memoryState(), _useConservativeMemoryMode,
                                  _currConfig.conservative.highwatermarkfactor,
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
        // Compute how much of disk is occupied by live data, give that bloat is maxed,
        // which is normally the case in a system that has been running for a while.
        double spaceUtilization = utilization * (1 - _currConfig.diskbloatfactor);
        // Then compute how much bloat can allowed given the current space usage and still stay below low watermark
        double targetBloat = (_currConfig.conservative.lowwatermarkfactor - spaceUtilization) / _currConfig.conservative.lowwatermarkfactor;
        newConfig.diskBloatFactor = 1.0;
        newConfig.globalDiskBloatFactor = std::max(targetBloat, _currConfig.diskbloatfactor);
    }
}

void
MemoryFlushConfigUpdater::updateFlushStrategy(const LockGuard &guard, const char * why)
{
    MemoryFlush::Config newConfig = convertConfig(_currConfig, _memory);
    considerUseConservativeDiskMode(guard, newConfig);
    considerUseConservativeMemoryMode(guard, newConfig);
    MemoryFlush::Config currentConfig = _flushStrategy->getConfig();
    if ( currentConfig != newConfig ) {
        _flushStrategy->setConfig(newConfig);
        LOG(info, "Due to %s (conservative-disk=%d, conservative-memory=%d, retired=%d) flush config updated to "
                  "global-disk-bloat(%1.2f), max-tls-size(%" PRIu64 "),max-global-memory(%" PRIu64 "), max-memory-gain(%" PRIu64 ")",
            why, _useConservativeDiskMode, _useConservativeMemoryMode, _nodeRetired,
            newConfig.globalDiskBloatFactor, newConfig.maxGlobalTlsSize,
            newConfig.maxGlobalMemory, newConfig.maxMemoryGain);
        LOG(debug, "Old config = %s\nNew config = %s", currentConfig.toString().c_str(), newConfig.toString().c_str());
    }
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
    updateFlushStrategy(guard, "new config");
}

void
MemoryFlushConfigUpdater::notifyDiskMemUsage(DiskMemUsageState newState)
{
    LockGuard guard(_mutex);
    _currState = newState;
    updateFlushStrategy(guard, "disk-mem-usage update");
}

void
MemoryFlushConfigUpdater::setNodeRetired(bool nodeRetired)
{
    LockGuard guard(_mutex);
    _nodeRetired = nodeRetired;
    updateFlushStrategy(guard, nodeRetired ? "node retired" : "node unretired");
}

namespace {

size_t
getHardMemoryLimit(const HwInfo::Memory &memory)
{
    return memory.sizeBytes() / 4;
}

}

MemoryFlush::Config
MemoryFlushConfigUpdater::convertConfig(const ProtonConfig::Flush::Memory &config, const HwInfo::Memory &memory)
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
