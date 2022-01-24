// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>

namespace proton {

/**
 * Usage metrics for various resources in this search engine.
 */
struct ResourceUsageMetrics : metrics::MetricSet
{
    metrics::DoubleValueMetric disk;
    metrics::DoubleValueMetric diskUtilization;
    metrics::DoubleValueMetric memory;
    metrics::DoubleValueMetric memoryUtilization;
    metrics::DoubleValueMetric transient_memory;
    metrics::DoubleValueMetric transient_disk;
    metrics::LongValueMetric memoryMappings;
    metrics::LongValueMetric openFileDescriptors;
    metrics::LongValueMetric feedingBlocked;
    metrics::LongValueMetric mallocArena;
    metrics::DoubleValueMetric cpu_setup;
    metrics::DoubleValueMetric cpu_read;
    metrics::DoubleValueMetric cpu_write;
    metrics::DoubleValueMetric cpu_compact;
    metrics::DoubleValueMetric cpu_other;

    ResourceUsageMetrics(metrics::MetricSet *parent);
    ~ResourceUsageMetrics();
};

} // namespace proton
