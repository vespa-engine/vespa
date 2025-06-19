// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_write_filter.h"
#include "i_resource_usage_listener.h"
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <iomanip>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.resource_usage_write_filter");

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

void make_attribute_address_space_message(std::ostream& os, const AttributeUsageStats& usage)
{
    auto& max = usage.max_address_space_usage();
    auto& as = max.getUsage();
    os << "{ used: " <<
       as.used() << ", dead: " <<
       as.dead() << ", limit: " <<
       as.limit() << "}, " <<
       "document_type: " << std::quoted(usage.document_type()) << ", " <<
       "attributeName: \"" << max.getAttributeName() << "\", " <<
       "componentName: \"" << max.get_component_name() << "\", " <<
       "subdb: \"" << max.getSubDbName() << "\"}";
}

void make_attribute_address_space_error_message(std::ostream& os, double used, double limit,
                                                const AttributeUsageStats& usage)
{
    os << "addressSpaceLimitReached: { "
          "action: \""
          "add more content nodes"
          "\", "
          "reason: \""
          "max address space in attribute vector components used (" << used << ") > "
                                                                               "limit (" << limit << ")"
                                                                                                     "\", addressSpace: ";
    make_attribute_address_space_message(os, usage);
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

ResourceUsageWriteFilter::ResourceUsageWriteFilter(const HwInfo& hwInfo)
    : IResourceWriteFilter(),
      IAttributeUsageListener(),
      _lock(),
      _hwInfo(hwInfo),
      _acceptWrite(true),
      _memoryStats(),
      _diskUsedSizeBytes(0),
      _state(),
      _usage_state(),
      _attribute_usage(),
      _attribute_usage_filter_config()
{ }

ResourceUsageWriteFilter::~ResourceUsageWriteFilter() = default;

void
ResourceUsageWriteFilter::recalc_state(const Guard& guard)
{
    (void) guard;
    bool hasMessage = false;
    std::ostringstream message;
    if (_usage_state.aboveMemoryLimit(1.0)) {
        hasMessage = true;
        makeMemoryLimitMessage(message, _usage_state.memoryState().usage(),
                               _usage_state.memoryState().limit(), _memoryStats, _hwInfo.memory().sizeBytes());
    }
    if (_usage_state.aboveDiskLimit(1.0)) {
        if (hasMessage) {
            message << ", ";
        }
        hasMessage = true;
        makeDiskLimitMessage(message, _usage_state.diskState().usage(), _usage_state.diskState().limit(), _hwInfo, _diskUsedSizeBytes);
    }
    {
        const auto &max_usage = _attribute_usage.max_address_space_usage();
        double used = max_usage.getUsage().usage();
        if (used > _attribute_usage_filter_config._address_space_limit) {
            if (hasMessage) {
                message << ", ";
            }
            hasMessage = true;
            make_attribute_address_space_error_message(message, used,
                                                       _attribute_usage_filter_config._address_space_limit, _attribute_usage);
        }
    }
    if (hasMessage) {
        if (_acceptWrite) {
            LOG(warning, "Write operations are now blocked: '%s'", message.str().c_str());
        }
        _state = State(false, message.str());
        _acceptWrite = false;
    } else {
        if (!_acceptWrite) {
            std::string unblockMsg = makeUnblockingMessage(_usage_state.memoryState().usage(),
                                                           _usage_state.memoryState().limit(),
                                                           _memoryStats,
                                                           _hwInfo,
                                                           _usage_state.diskState().usage(),
                                                           _usage_state.diskState().limit(),
                                                           _diskUsedSizeBytes);
            LOG(info, "Write operations are now un-blocked: '%s'", unblockMsg.c_str());
        }
        _state = State();
        _acceptWrite = true;
    }

}

bool
ResourceUsageWriteFilter::acceptWriteOperation() const
{
    return _acceptWrite;
}

ResourceUsageWriteFilter::State
ResourceUsageWriteFilter::getAcceptState() const
{
    Guard guard(_lock);
    return _state;
}

void
ResourceUsageWriteFilter::notify_resource_usage(const ResourceUsageState& state, const vespalib::ProcessMemoryStats &memoryStats,
                                                uint64_t diskUsedSizeBytes)
{
    std::lock_guard guard(_lock);
    _usage_state = state;
    _memoryStats = memoryStats;
    _diskUsedSizeBytes = diskUsedSizeBytes;
    recalc_state(guard);
}

void
ResourceUsageWriteFilter::set_config(AttributeUsageFilterConfig attribute_usage_filter_config)
{
    Guard guard(_lock);
    _attribute_usage_filter_config = attribute_usage_filter_config;
    recalc_state(guard);
}

void
ResourceUsageWriteFilter::notify_attribute_usage(const AttributeUsageStats& attribute_usage)
{
    Guard guard(_lock);
    _attribute_usage = attribute_usage;
    recalc_state(guard);
}

} // namespace proton
