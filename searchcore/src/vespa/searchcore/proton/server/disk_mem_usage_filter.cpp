// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_filter.h"
#include "i_disk_mem_usage_listener.h"

namespace proton {

namespace {

void
makeMemoryLimitMessage(std::ostream &os,
                       double memoryUsed,
                       double memoryLimit,
                       const vespalib::ProcessMemoryStats &memoryStats,
                       uint64_t physicalMemory)
{
    os << "memoryLimitReached: { "
            "action: \"add more content nodes\", "
            "reason: \""
            "memory used (" << memoryUsed << ") > "
            "memory limit (" << memoryLimit << ")"
            "\", mapped: { virt: " <<
            memoryStats.getMappedVirt() << ", rss: " <<
            memoryStats.getMappedRss() << "}, anonymous: { virt: " <<
            memoryStats.getAnonymousVirt() << ", rss: " <<
            memoryStats.getAnonymousRss() << "}, physicalMemory: " <<
            physicalMemory << ", memoryLimit : " <<
            memoryLimit << "}";
}

void
makeDiskLimitMessage(std::ostream &os,
                     double diskUsed,
                     double diskLimit,
                     const DiskMemUsageFilter::space_info &diskStats)
{
    os << "diskLimitReached: { "
            "action: \"add more content nodes\", "
            "reason: \""
            "disk used (" << diskUsed << ") > "
            "disk limit (" << diskLimit << ")"
            "\", capacity: " <<
            diskStats.capacity << ", free: " <<
            diskStats.free << ", available: " <<
            diskStats.available << ", diskLimit: " <<
            diskLimit << "}";
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
                _config._memoryLimit, _memoryStats, _physicalMemory);
    }
    double diskUsed = getDiskUsedRatio(guard);
    if (diskUsed > _config._diskLimit) {
        if (hasMessage) {
            message << ", ";
        }
        hasMessage = true;
        makeDiskLimitMessage(message, diskUsed, _config._diskLimit, _diskStats);
    }
    if (hasMessage) {
        _state = State(false, message.str());
        _acceptWrite = false;
    } else {
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
    return static_cast<double>(unscaledMemoryUsed) / _physicalMemory;
}

double
DiskMemUsageFilter::getDiskUsedRatio(const Guard &guard) const
{
    (void) guard;
    double availableDiskSpaceRatio = static_cast<double>(_diskStats.available) /
                                     static_cast<double>(_diskStats.capacity);
    return 1.0 - availableDiskSpaceRatio;
}

DiskMemUsageFilter::DiskMemUsageFilter(uint64_t physicalMemory_in)
    : _lock(),
      _memoryStats(),
      _physicalMemory(physicalMemory_in),
      _diskStats(),
      _config(),
      _state(),
      _acceptWrite(true),
      _dmstate(),
      _listeners()
{ }

DiskMemUsageFilter::~DiskMemUsageFilter() { }

void
DiskMemUsageFilter::setMemoryStats(vespalib::ProcessMemoryStats memoryStats_in)
{
    Guard guard(_lock);
    _memoryStats = memoryStats_in;
    recalcState(guard);
}

void
DiskMemUsageFilter::setDiskStats(space_info diskStats_in)
{
    Guard guard(_lock);
    _diskStats = diskStats_in;
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

DiskMemUsageFilter::space_info
DiskMemUsageFilter::getDiskStats() const
{
    Guard guard(_lock);
    return _diskStats;
}

DiskMemUsageFilter::Config
DiskMemUsageFilter::getConfig() const
{
    Guard guard(_lock);
    return _config;
}

double
DiskMemUsageFilter::getMemoryUsedRatio() const
{
    Guard guard(_lock);
    return getMemoryUsedRatio(guard);
}

double
DiskMemUsageFilter::getDiskUsedRatio() const
{
    Guard guard(_lock);
    return getDiskUsedRatio(guard);
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
