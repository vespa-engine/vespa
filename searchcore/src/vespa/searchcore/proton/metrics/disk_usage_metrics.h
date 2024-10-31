// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/metricset.h>

namespace search { class DiskIoStats; }

namespace proton {

/**
 * Metric set for disk usage metrics.
 */
class DiskUsageMetrics : public metrics::MetricSet {
    metrics::LongValueMetric _size_on_disk;
    metrics::LongValueMetric _search_read_bytes;

public:
    explicit DiskUsageMetrics(metrics::MetricSet* parent);
    ~DiskUsageMetrics() override;
    void update(uint64_t size_on_disk, const search::DiskIoStats& disk_io_stats);
};

}
