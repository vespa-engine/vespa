// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/valuemetric.h>
#include <vespa/metrics/metricset.h>

namespace vespalib { class MemoryUsage; }

namespace metrics {

/**
 * Metric set for memory usage metrics.
 */
class MemoryUsageMetrics : public metrics::MetricSet {
    metrics::LongValueMetric _allocated_bytes;
    metrics::LongValueMetric _used_bytes;
    metrics::LongValueMetric _dead_bytes;
    metrics::LongValueMetric _on_hold_bytes;

public:
    explicit MemoryUsageMetrics(metrics::MetricSet* parent);
    ~MemoryUsageMetrics() override;
    void update(const vespalib::MemoryUsage& usage);
};

} // namespace metrics
