// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_usage_metrics.h"
#include <vespa/searchlib/util/disk_io_stats.h>
#include <vespa/vespalib/util/memoryusage.h>

using search::DiskIoStats;

namespace proton {

DiskUsageMetrics::DiskUsageMetrics(metrics::MetricSet* parent)
    : MetricSet("disk_usage", {}, "The disk usage for a given component", parent),
      _size_on_disk("size_on_disk", {}, "Size on disk (bytes)", this),
      _search_read_bytes("search_read_bytes", {}, "Bytes read in posting list files as part of search", this)
{
}

DiskUsageMetrics::~DiskUsageMetrics() = default;

void
DiskUsageMetrics::update(uint64_t size_on_disk, const DiskIoStats& disk_io_stats)
{
    _size_on_disk.set(size_on_disk);
    _search_read_bytes.addTotalValueBatch(disk_io_stats.read_bytes_total(),
                                          disk_io_stats.read_operations(),
                                          disk_io_stats.read_bytes_min(),
                                          disk_io_stats.read_bytes_max());
}

}
