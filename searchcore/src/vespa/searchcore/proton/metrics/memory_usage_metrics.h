// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>

namespace search { class MemoryUsage; }

namespace proton {

/**
 * Metric set for memory usage metrics.
 */
class MemoryUsageMetrics : public metrics::MetricSet
{
private:
    metrics::LongValueMetric _allocatedBytes;
    metrics::LongValueMetric _usedBytes;
    metrics::LongValueMetric _deadBytes;
    metrics::LongValueMetric _onHoldBytes;

public:
    MemoryUsageMetrics(metrics::MetricSet *parent);
    ~MemoryUsageMetrics();
    void update(const search::MemoryUsage &usage);
};

} // namespace proton
