// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_metrics_entry.h"
#include <vespa/searchlib/util/disk_io_stats.h>

using search::DiskIoStats;

namespace proton {

namespace {

const std::string entry_name("index");
const std::string entry_description("Metrics for indexes for a given field");

}

IndexMetricsEntry::DiskIoMetrics::SearchMetrics::SearchMetrics(metrics::MetricSet* parent)
    : MetricSet("search", {}, "The search io for a given component", parent),
      _read_bytes("read_bytes", {}, "Bytes read in posting list files as part of search", this)
{
}

IndexMetricsEntry::DiskIoMetrics::SearchMetrics::~SearchMetrics() = default;

void
IndexMetricsEntry::DiskIoMetrics::SearchMetrics::update(const DiskIoStats& disk_io_stats)
{
    _read_bytes.addTotalValueBatch(disk_io_stats.read_bytes_total(),
                                   disk_io_stats.read_operations(),
                                   disk_io_stats.read_bytes_min(),
                                   disk_io_stats.read_bytes_max());
}

IndexMetricsEntry::DiskIoMetrics::DiskIoMetrics(metrics::MetricSet* parent)
    : MetricSet("io", {}, "The disk usage for a given component", parent),
      _search(this)
{
}

IndexMetricsEntry::DiskIoMetrics::~DiskIoMetrics() = default;

IndexMetricsEntry::IndexMetricsEntry(const std::string& field_name)
    : FieldMetricsEntry(entry_name, field_name, entry_description),
      _disk_io(this)
{
}

IndexMetricsEntry::~IndexMetricsEntry() = default;

} // namespace proton
