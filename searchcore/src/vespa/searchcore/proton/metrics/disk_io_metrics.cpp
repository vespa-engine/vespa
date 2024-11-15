// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_io_metrics.h"
#include <vespa/searchlib/util/cache_disk_io_stats.h>

using search::CacheDiskIoStats;
using search::DiskIoStats;

namespace proton {

namespace {

void update_helper(metrics::LongValueMetric &metric, const DiskIoStats &stats) {
    metric.addTotalValueBatch(stats.read_bytes_total(), stats.read_operations(),
                              stats.read_bytes_min(), stats.read_bytes_max());
}

}

DiskIoMetrics::SearchMetrics::SearchMetrics(metrics::MetricSet* parent)
    : MetricSet("search", {}, "The search io for a given component", parent),
      _read_bytes("read_bytes", {}, "Bytes read in posting list files as part of search", this),
      _cached_read_bytes("cached_read_bytes", {}, "Bytes read from posting list files cache as part of search", this)
{
}

DiskIoMetrics::SearchMetrics::~SearchMetrics() = default;

void
DiskIoMetrics::SearchMetrics::update(const CacheDiskIoStats& cache_disk_io_stats)
{
    update_helper(_read_bytes, cache_disk_io_stats.read());
    update_helper(_cached_read_bytes, cache_disk_io_stats.cached_read());
}

DiskIoMetrics::DiskIoMetrics(metrics::MetricSet* parent)
    : MetricSet("io", {}, "The disk usage for a given component", parent),
      _search(this)
{
}

DiskIoMetrics::~DiskIoMetrics() = default;

}
