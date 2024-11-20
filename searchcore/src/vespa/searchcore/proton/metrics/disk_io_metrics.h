// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>

namespace search { class CacheDiskIoStats; }

namespace proton {

/*
 * Class containing disk io metrics, e.g. per index field or
 * aggregated at document type level.
 */
class DiskIoMetrics : public metrics::MetricSet {
    class SearchMetrics : public metrics::MetricSet {
        metrics::LongValueMetric _read_bytes;
        metrics::LongValueMetric _cached_read_bytes;
    public:
        explicit SearchMetrics(metrics::MetricSet* parent);
        ~SearchMetrics() override;
        void update(const search::CacheDiskIoStats& cache_disk_io_stats);
    };

    SearchMetrics _search;

public:
    explicit DiskIoMetrics(metrics::MetricSet* parent);
    ~DiskIoMetrics() override;
    void update(const search::CacheDiskIoStats& cache_disk_io_stats) { _search.update(cache_disk_io_stats); }
};

}
