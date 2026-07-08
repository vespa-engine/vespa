// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_explorer.h"

#include "resource_usage_notifier.h"

#include <vespa/searchcore/proton/persistenceengine/resource_usage_tracker.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/process_memory_stats.h>

using namespace vespalib::slime;
using storage::spi::AttributeResourceUsage;

namespace proton {

void convertDiskStatsToSlime(const DiskUsage& disk_usage, Cursor& object) {
    object.setLong("capacity", disk_usage.capacity_bytes());
    object.setLong("used", disk_usage.used_bytes());
}

void convertMemoryStatsToSlime(const vespalib::ProcessMemoryStats& stats, Cursor& object) {
    object.setLong("virt", stats.getVirt());
    object.setLong("mappedRss", stats.getMappedRss());
    object.setLong("anonymousRss", stats.getAnonymousRss());
}

ResourceUsageExplorer::ResourceUsageExplorer(const ResourceUsageNotifier& usage_notifier,
                                             const ResourceUsageTracker&  usage_tracker)
    : _usage_notifier(usage_notifier), _usage_tracker(usage_tracker) {
}

void ResourceUsageExplorer::get_state(const vespalib::slime::Inserter& inserter, bool full) const {
    Cursor&                object = inserter.insertObject();
    ResourceUsageState     usageState = _usage_notifier.usageState();
    AttributeResourceUsage attr_usage = _usage_tracker.get_resource_usage().get_attribute_address_space_usage();
    if (full) {
        Cursor& disk = object.setObject("disk");
        disk.setDouble("usage", usageState.diskState().usage());
        disk.setDouble("limit", usageState.diskState().limit());
        disk.setDouble("utilization", usageState.diskState().utilization());
        disk.setDouble("reserved", usageState.reserved_disk_space());
        disk.setDouble("reserved-factor", usageState.reserved_disk_space_factor());
        disk.setDouble("transient", usageState.transient_disk_usage());
        disk.setDouble("non-transient", usageState.non_transient_disk_usage());
        disk.setDouble("reported", usageState.reported_disk_usage());
        auto reserved_disk_space_and_memory = _usage_notifier.reserved_disk_space_and_memory();
        auto disk_usage = _usage_notifier.disk_usage();
        disk.setDouble("reserved-for-flush",
                       static_cast<double>(reserved_disk_space_and_memory.reserved_disk_space_for_flush()) /
                           disk_usage.capacity_bytes());
        disk.setDouble("reserved-for-growth",
                       static_cast<double>(reserved_disk_space_and_memory.reserved_disk_space_for_growth()) /
                           disk_usage.capacity_bytes());
        convertDiskStatsToSlime(disk_usage, disk.setObject("stats"));

        Cursor& memory = object.setObject("memory");
        memory.setDouble("usage", usageState.memoryState().usage());
        memory.setDouble("limit", usageState.memoryState().limit());
        memory.setDouble("utilization", usageState.memoryState().utilization());
        memory.setDouble("reserved", usageState.reserved_memory());
        memory.setDouble("reserved-factor", usageState.reserved_memory_factor());
        memory.setDouble("transient", usageState.transient_memory_usage());
        memory.setDouble("non-transient", usageState.non_transient_memory_usage());
        memory.setDouble("reported", usageState.reported_memory_usage());
        auto physical_memory = _usage_notifier.getHwInfo().memory().sizeBytes();
        memory.setLong("physicalMemory", physical_memory);
        memory.setDouble("reserved-for-flush",
                         static_cast<double>(reserved_disk_space_and_memory.reserved_memory_for_flush()) /
                             physical_memory);
        memory.setDouble("reserved-for-memory-indexes",
                         static_cast<double>(reserved_disk_space_and_memory.reserved_memory_for_memory_indexes()) /
                             physical_memory);
        convertMemoryStatsToSlime(_usage_notifier.getMemoryStats(), memory.setObject("stats"));

        Cursor& address_space = object.setObject("attribute_address_space");
        address_space.setDouble("usage", attr_usage.get_usage());
        address_space.setString("name", attr_usage.get_name());
    } else {
        object.setDouble("disk", usageState.diskState().usage());
        object.setDouble("memory", usageState.memoryState().usage());
        object.setDouble("attribute_address_space", attr_usage.get_usage());
    }
}

} // namespace proton
