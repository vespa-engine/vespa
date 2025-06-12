// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_filter.h"
#include "i_disk_mem_usage_listener.h"
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.disk_mem_usage_filter");

using vespalib::HwInfo;
using vespalib::ProcessMemoryStats;

namespace proton {

namespace {

void
makeMemoryStatsMessage(std::ostream &os,
                       double memoryUsed,
                       double memoryLimit,
                       const ProcessMemoryStats &memoryStats,
                       uint64_t physicalMemory)
{
    os << "stats: { ";
    os << "virt: " << memoryStats.getVirt() << ", ";
    os << "rss: { mapped: " << memoryStats.getMappedRss() << ", ";
    os << "anonymous: " << memoryStats.getAnonymousRss() << "}, ";
    os << "physicalMemory: " << physicalMemory << ", ";
    os << "memoryUsed: " << memoryUsed << ", ";
    os << "memoryLimit: " << memoryLimit << "}";
}

void
makeMemoryLimitMessage(std::ostream &os,
                       double memoryUsed,
                       double memoryLimit,
                       const ProcessMemoryStats &memoryStats,
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

std::string
makeUnblockingMessage(double memoryUsed,
                      double memoryLimit,
                      const ProcessMemoryStats &memoryStats,
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

DiskMemUsageFilter::DiskMemUsageFilter(const HwInfo& hwInfo)
    : _lock(),
      _hwInfo(hwInfo),
      _acceptWrite(true),
      _memoryStats(),
      _diskUsedSizeBytes(0),
      _state(),
      _dmstate()
{ }

DiskMemUsageFilter::~DiskMemUsageFilter() = default;

void
DiskMemUsageFilter::recalc_state(const Guard& guard)
{
    (void) guard;
    bool hasMessage = false;
    std::ostringstream message;
    if (_dmstate.aboveMemoryLimit(1.0)) {
        hasMessage = true;
        makeMemoryLimitMessage(message, _dmstate.memoryState().usage(),
                               _dmstate.memoryState().limit(), _memoryStats, _hwInfo.memory().sizeBytes());
    }
    if (_dmstate.aboveDiskLimit(1.0)) {
        if (hasMessage) {
            message << ", ";
        }
        hasMessage = true;
        makeDiskLimitMessage(message, _dmstate.diskState().usage(), _dmstate.diskState().limit(), _hwInfo, _diskUsedSizeBytes);
    }
    if (hasMessage) {
        if (_acceptWrite) {
            LOG(warning, "Write operations are now blocked: '%s'", message.str().c_str());
        }
        _state = State(false, message.str());
        _acceptWrite = false;
    } else {
        if (!_acceptWrite) {
            std::string unblockMsg = makeUnblockingMessage(_dmstate.memoryState().usage(),
                                                           _dmstate.memoryState().limit(),
                                                           _memoryStats,
                                                           _hwInfo,
                                                           _dmstate.diskState().usage(),
                                                           _dmstate.diskState().limit(),
                                                           _diskUsedSizeBytes);
            LOG(info, "Write operations are now un-blocked: '%s'", unblockMsg.c_str());
        }
        _state = State();
        _acceptWrite = true;
    }

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
DiskMemUsageFilter::notify_disk_mem_usage(const DiskMemUsageState& state, const ProcessMemoryStats& memoryStats,
                                          uint64_t diskUsedSizeBytes)
{
    std::lock_guard guard(_lock);
    _dmstate = state;
    _memoryStats = memoryStats;
    _diskUsedSizeBytes = diskUsedSizeBytes;
    recalc_state(guard);
}

} // namespace proton
