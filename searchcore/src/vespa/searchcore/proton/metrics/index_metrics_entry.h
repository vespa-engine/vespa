// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "field_metrics_entry.h"

namespace search { class DiskIoStats; }

namespace proton {

/*
 * Class containing metrics for the index aspect of a field, i.e.
 * disk indexes and memory indexes.
 */
class IndexMetricsEntry : public FieldMetricsEntry {
    class DiskIoMetrics : public metrics::MetricSet {
        class SearchMetrics : public metrics::MetricSet {
            metrics::LongValueMetric _read_bytes;
        public:
            explicit SearchMetrics(metrics::MetricSet* parent);
            ~SearchMetrics() override;
            void update(const search::DiskIoStats& disk_io_stats);
        };

        SearchMetrics _search;

    public:
        explicit DiskIoMetrics(metrics::MetricSet* parent);
        ~DiskIoMetrics() override;
        void update(const search::DiskIoStats& disk_io_stats) { _search.update(disk_io_stats); }
    };

    DiskIoMetrics _disk_io;

public:
    explicit IndexMetricsEntry(const std::string& field_name);
    ~IndexMetricsEntry() override;
    void update_disk_io(const search::DiskIoStats& disk_io_stats) { _disk_io.update(disk_io_stats); }
};

} // namespace proton
