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
    if (full) {
        DiskMemUsageFilter::Config config = _usageFilter.getConfig();
        Cursor &disk = object.setObject("disk");
        disk.setDouble("usedRatio", _usageFilter.getDiskUsedRatio());
        disk.setDouble("usedLimit", config._diskLimit);
        convertDiskStatsToSlime(_usageFilter.getHwInfo(), _usageFilter.getDiskUsedSize(), disk.setObject("stats"));

        Cursor &memory = object.setObject("memory");
        memory.setDouble("usedRatio", _usageFilter.getMemoryUsedRatio());
        memory.setDouble("usedLimit", config._memoryLimit);
        memory.setLong("physicalMemory", _usageFilter.getHwInfo().memory().sizeBytes());
        convertMemoryStatsToSlime(_usageFilter.getMemoryStats(), memory.setObject("stats"));
    } else {
        object.setDouble("disk", _usageFilter.getDiskUsedRatio());
        object.setDouble("memory", _usageFilter.getMemoryUsedRatio());
    }
}

} // namespace proton
