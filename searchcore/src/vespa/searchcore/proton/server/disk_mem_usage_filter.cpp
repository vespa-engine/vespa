// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_filter.h"
#include "i_disk_mem_usage_listener.h"
#include <vespa/searchcore/proton/common/hw_info.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.disk_mem_usage_filter");

namespace proton {

namespace {

void
makeMemoryStatsMessage(std::ostream &os,
                       double memoryUsed,
                       double memoryLimit,
                       const vespalib::ProcessMemoryStats &memoryStats,
                       uint64_t physicalMemory)
{
    os << "stats: { ";
    os << "mapped: { virt: " << memoryStats.getMappedVirt() << ", rss: " << memoryStats.getMappedRss() << "}, ";
    os << "anonymous: { virt: " << memoryStats.getAnonymousVirt() << ", rss: " << memoryStats.getAnonymousRss() << "}, ";
    os << "physicalMemory: " << physicalMemory << ", ";
    os << "memoryUsed: " << memoryUsed << ", ";
    os << "memoryLimit: " << memoryLimit << "}";
}

void
makeMemoryLimitMessage(std::ostream &os,
                       double memoryUsed,
                       double memoryLimit,
                       const vespalib::ProcessMemoryStats &memoryStats,
                       uint64_t physicalMemory)
{
    os << "memoryLimitReached: { ";
    os << "action: \"add more content nodes\", ";
    os << "reason: \"memory used (" << memoryUsed << ") > memory limit (" << memoryLimit << ")\", ";
    makeMemoryStatsMessage(os, memoryUsed, memoryLimit, memoryStats, physicalMemory);
    os << "}";
}

void
makeDiskStatsMessage(std::ostream &os,
                     double diskUsed,
                     double diskLimit,
                     const HwInfo &hwInfo,
                     uint64_t usedDiskSizeBytes)
{
    os << "stats: { ";
    os << "capacity: " << hwInfo.disk().sizeBytes() << ", ";
    os << "used: " << usedDiskSizeBytes << ", ";
    os << "diskUsed: " << diskUsed << ", ";
    os << "diskLimit: " << diskLimit << "}";
}

void
makeDiskLimitMessage(std::ostream &os,
                     double diskUsed,
                     double diskLimit,
                     const HwInfo &hwInfo,
                     uint64_t usedDiskSizeBytes)
{
    os << "diskLimitReached: { ";
    os << "action: \"add more content nodes\", ";
    os << "reason: \"disk used (" << diskUsed << ") > disk limit (" << diskLimit << ")\", ";
    makeDiskStatsMessage(os, diskUsed, diskLimit, hwInfo, usedDiskSizeBytes);
    os << "}";
}

vespalib::string
makeUnblockingMessage(double memoryUsed,
                      double memoryLimit,
                      const vespalib::ProcessMemoryStats &memoryStats,
                      const HwInfo &hwInfo,
                      double diskUsed,
                      double diskLimit,
                      uint64_t usedDiskSizeBytes)
{
    std::ostringstream os;
    os << "memoryLimitOK: { ";
    makeMemoryStatsMessage(os, memoryUsed, memoryLimit, memoryStats, hwInfo.memory().sizeBytes());
    os << "}, ";
    os << "diskLimitOK: { ";
    makeDiskStatsMessage(os, diskUsed, diskLimit, hwInfo, usedDiskSizeBytes);
    os << "}";
    return os.str();
}

}

void
DiskMemUsageFilter::recalcState(const Guard &guard)
{
    bool hasMessage = false;
    std::ostringstream message;
    double memoryUsed = getMemoryUsedRatio(guard);
    if (memoryUsed > _config._memoryLimit) {
        hasMessage = true;
        makeMemoryLimitMessage(message, memoryUsed,
                _config._memoryLimit, _memoryStats, _hwInfo.memory().sizeBytes());
    }
    double diskUsed = getDiskUsedRatio(guard);
    if (diskUsed > _config._diskLimit) {
        if (hasMessage) {
            message << ", ";
        }
        hasMessage = true;
        makeDiskLimitMessage(message, diskUsed, _config._diskLimit, _hwInfo, _diskUsedSizeBytes);
    }
    if (hasMessage) {
        if (_acceptWrite) {
            LOG(warning, "Write operations are now blocked: '%s'", message.str().c_str());
        }
        _state = State(false, message.str());
        _acceptWrite = false;
    } else {
        if (!_acceptWrite) {
            vespalib::string unblockMsg = makeUnblockingMessage(memoryUsed,
                                                                _config._memoryLimit,
                                                                _memoryStats,
                                                                _hwInfo,
                                                                diskUsed,
                                                                _config._diskLimit,
                                                                _diskUsedSizeBytes);
            LOG(info, "Write operations are now un-blocked: '%s'", unblockMsg.c_str());
        }
        _state = State();
        _acceptWrite = true;
    }
    DiskMemUsageState dmstate(ResourceUsageState(_config._diskLimit, diskUsed),
                              ResourceUsageState(_config._memoryLimit, memoryUsed));
    notifyDiskMemUsage(guard, dmstate);
}

double
DiskMemUsageFilter::getMemoryUsedRatio(const Guard &guard) const
{
    (void) guard;
    uint64_t unscaledMemoryUsed = _memoryStats.getAnonymousRss();
    return static_cast<double>(unscaledMemoryUsed) / _hwInfo.memory().sizeBytes();
}

double
DiskMemUsageFilter::getDiskUsedRatio(const Guard &guard) const
{
    (void) guard;
    double usedDiskSpaceRatio = static_cast<double>(_diskUsedSizeBytes) /
                                static_cast<double>(_hwInfo.disk().sizeBytes());
    return usedDiskSpaceRatio;
}

DiskMemUsageFilter::DiskMemUsageFilter(const HwInfo &hwInfo)
    : _lock(),
      _hwInfo(hwInfo),
      _memoryStats(),
      _diskUsedSizeBytes(),
      _config(),
      _state(),
      _acceptWrite(true),
      _dmstate(),
      _listeners()
{ }

DiskMemUsageFilter::~DiskMemUsageFilter() = default;

void
DiskMemUsageFilter::setMemoryStats(vespalib::ProcessMemoryStats memoryStats_in)
{
    Guard guard(_lock);
    _memoryStats = memoryStats_in;
    recalcState(guard);
}

void
DiskMemUsageFilter::setDiskUsedSize(uint64_t diskUsedSizeBytes)
{
    Guard guard(_lock);
    _diskUsedSizeBytes = diskUsedSizeBytes;
    recalcState(guard);
}

void
DiskMemUsageFilter::setConfig(Config config_in)
{
    Guard guard(_lock);
    _config = config_in;
    recalcState(guard);
}

vespalib::ProcessMemoryStats
DiskMemUsageFilter::getMemoryStats() const
{
    Guard guard(_lock);
    return _memoryStats;
}

uint64_t
DiskMemUsageFilter::getDiskUsedSize() const
{
    Guard guard(_lock);
    return _diskUsedSizeBytes;
}

DiskMemUsageFilter::Config
DiskMemUsageFilter::getConfig() const
{
    Guard guard(_lock);
    return _config;
}

DiskMemUsageState
DiskMemUsageFilter::usageState() const
{
    Guard guard(_lock);
    return _dmstate;
}

bool
DiskMemUsageFilter::acceptWriteOperation() const
{
    return _acceptWrite;
}

DiskMemUsageFilter::State
DiskMemUsageFilter::getAcceptState() const
{
    Guard guard(_lock);
    return _state;
}


void
DiskMemUsageFilter::addDiskMemUsageListener(IDiskMemUsageListener *listener)
{
    Guard guard(_lock);
    _listeners.push_back(listener);
    listener->notifyDiskMemUsage(_dmstate);
}

void
DiskMemUsageFilter::removeDiskMemUsageListener(IDiskMemUsageListener *listener)
{
    Guard guard(_lock);
    for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
        if (*itr == listener) {
            _listeners.erase(itr);
            break;
        }
    }
}

void
DiskMemUsageFilter::notifyDiskMemUsage(const Guard &guard,
                                       DiskMemUsageState state)
{
    (void) guard;
    if (_dmstate == state) {
        return;
    }
    _dmstate = state;
    for (const auto &listener : _listeners) {
        listener->notifyDiskMemUsage(_dmstate);
    }
}


} // namespace proton
