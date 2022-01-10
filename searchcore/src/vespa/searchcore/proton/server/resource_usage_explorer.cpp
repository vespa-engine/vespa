// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_explorer.h"
#include "disk_mem_usage_filter.h"
#include <vespa/searchcore/proton/persistenceengine/resource_usage_tracker.h>
#include <vespa/vespalib/data/slime/cursor.h>

using namespace vespalib::slime;
using storage::spi::AttributeResourceUsage;

namespace proton {

void
convertDiskStatsToSlime(const HwInfo &hwInfo, uint64_t diskUsedSizeBytes, Cursor &object)
{
    object.setLong("capacity", hwInfo.disk().sizeBytes());
    object.setLong("used", diskUsedSizeBytes);
}

void
convertMemoryStatsToSlime(const vespalib::ProcessMemoryStats &stats, Cursor &object)
{
    object.setLong("mappedVirt", stats.getMappedVirt());
    object.setLong("mappedRss", stats.getMappedRss());
    object.setLong("anonymousVirt", stats.getAnonymousVirt());
    object.setLong("anonymousRss", stats.getAnonymousRss());
}

ResourceUsageExplorer::ResourceUsageExplorer(const DiskMemUsageFilter& usage_filter,
                                             const ResourceUsageTracker& usage_tracker)
    : _usage_filter(usage_filter),
      _usage_tracker(usage_tracker)
{
}

void
ResourceUsageExplorer::get_state(const vespalib::slime::Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    DiskMemUsageState usageState = _usage_filter.usageState();
    AttributeResourceUsage attr_usage = _usage_tracker.get_resource_usage().get_attribute_address_space_usage();
    if (full) {
        Cursor &disk = object.setObject("disk");
        disk.setDouble("usage", usageState.diskState().usage());
        disk.setDouble("limit", usageState.diskState().limit());
        disk.setDouble("utilization", usageState.diskState().utilization());
        disk.setDouble("transient", usageState.transient_disk_usage());
        convertDiskStatsToSlime(_usage_filter.getHwInfo(), _usage_filter.getDiskUsedSize(), disk.setObject("stats"));

        Cursor &memory = object.setObject("memory");
        memory.setDouble("usage", usageState.memoryState().usage());
        memory.setDouble("limit", usageState.memoryState().limit());
        memory.setDouble("utilization", usageState.memoryState().utilization());
        memory.setDouble("transient", usageState.transient_memory_usage());
        memory.setLong("physicalMemory", _usage_filter.getHwInfo().memory().sizeBytes());
        convertMemoryStatsToSlime(_usage_filter.getMemoryStats(), memory.setObject("stats"));

        Cursor &address_space = object.setObject("attribute_address_space");
        address_space.setDouble("usage", attr_usage.get_usage());
        address_space.setString("name", attr_usage.get_name());
    } else {
        object.setDouble("disk", usageState.diskState().usage());
        object.setDouble("memory", usageState.memoryState().usage());
        object.setDouble("attribute_address_space", attr_usage.get_usage());
    }
}

} // namespace proton
