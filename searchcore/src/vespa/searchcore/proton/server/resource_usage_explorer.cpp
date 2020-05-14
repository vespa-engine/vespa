// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_explorer.h"
#include "disk_mem_usage_filter.h"
#include <vespa/vespalib/data/slime/cursor.h>

using namespace vespalib::slime;

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

ResourceUsageExplorer::ResourceUsageExplorer(const DiskMemUsageFilter &usageFilter)
    : _usageFilter(usageFilter)
{
}

void
ResourceUsageExplorer::get_state(const vespalib::slime::Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    DiskMemUsageState usageState = _usageFilter.usageState();
    if (full) {
        Cursor &disk = object.setObject("disk");
        disk.setDouble("usage", usageState.diskState().usage());
        disk.setDouble("limit", usageState.diskState().limit());
        disk.setDouble("utilization", usageState.diskState().utilization());
        convertDiskStatsToSlime(_usageFilter.getHwInfo(), _usageFilter.getDiskUsedSize(), disk.setObject("stats"));

        Cursor &memory = object.setObject("memory");
        memory.setDouble("usage", usageState.memoryState().usage());
        memory.setDouble("limit", usageState.memoryState().limit());
        memory.setDouble("utilization", usageState.memoryState().utilization());
        memory.setLong("physicalMemory", _usageFilter.getHwInfo().memory().sizeBytes());
        convertMemoryStatsToSlime(_usageFilter.getMemoryStats(), memory.setObject("stats"));
        size_t transient_memory = _usageFilter.get_transient_memory_usage();
        memory.setLong("transient", transient_memory);
    } else {
        object.setDouble("disk", usageState.diskState().usage());
        object.setDouble("memory", usageState.memoryState().usage());
    }
}

} // namespace proton
