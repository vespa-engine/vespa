// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

        DetailedResourceMetrics(const std::string& resource_type, metrics::MetricSet* parent);
        ~DetailedResourceMetrics();
    };

    struct DetailedDiskResourceMetrics : public DetailedResourceMetrics {
        metrics::DoubleValueMetric reserved;
        metrics::DoubleValueMetric used_and_reserved; // transient disk space not included
        DetailedDiskResourceMetrics(metrics::MetricSet* parent);
        ~DetailedDiskResourceMetrics();
    };

    metrics::DoubleValueMetric  disk;
    metrics::DoubleValueMetric  memory;
    DetailedDiskResourceMetrics disk_usage;
    DetailedResourceMetrics     memory_usage;
    metrics::LongValueMetric    openFileDescriptors;
    metrics::LongValueMetric    feedingBlocked;
    metrics::LongValueMetric    mallocArena;
    CpuUtilMetrics              cpu_util;

    ResourceUsageMetrics(metrics::MetricSet *parent);
    ~ResourceUsageMetrics();
};

} // namespace proton
