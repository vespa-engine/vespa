// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "disk_io_metrics.h"
#include "field_metrics_entry.h"

namespace proton {

/*
 * Class containing metrics for the index aspect of a field, i.e.
 * disk indexes and memory indexes.
 */
class IndexMetricsEntry : public FieldMetricsEntry {
    DiskIoMetrics _disk_io;

public:
    explicit IndexMetricsEntry(const std::string& field_name);
    ~IndexMetricsEntry() override;
    void update_disk_io(const search::CacheDiskIoStats& cache_disk_io_stats) { _disk_io.update(cache_disk_io_stats); }
};

} // namespace proton
