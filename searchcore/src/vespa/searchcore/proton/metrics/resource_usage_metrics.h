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
    struct CpuUtilMetrics : metrics::MetricSet {
        metrics::DoubleValueMetric setup;
        metrics::DoubleValueMetric read;
        metrics::DoubleValueMetric write;
        metrics::DoubleValueMetric compact;
        metrics::DoubleValueMetric other;

        CpuUtilMetrics(metrics::MetricSet *parent);
        ~CpuUtilMetrics();
    };

    struct DetailedResourceMetrics : metrics::MetricSet {
        metrics::DoubleValueMetric total;
        metrics::DoubleValueMetric total_util;
        metrics::DoubleValueMetric transient;

        DetailedResourceMetrics(const vespalib::string& resource_type, metrics::MetricSet* parent);
        ~DetailedResourceMetrics();
    };

    metrics::DoubleValueMetric disk;
    metrics::DoubleValueMetric memory;
    DetailedResourceMetrics disk_usage;
    DetailedResourceMetrics memory_usage;
    metrics::LongValueMetric memoryMappings;
    metrics::LongValueMetric openFileDescriptors;
    metrics::LongValueMetric feedingBlocked;
    metrics::LongValueMetric mallocArena;
    CpuUtilMetrics           cpu_util;

    ResourceUsageMetrics(metrics::MetricSet *parent);
    ~ResourceUsageMetrics();
};

} // namespace proton
