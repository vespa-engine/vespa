// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attribute_metrics.h"
#include "memory_usage_metrics.h"
#include "executor_threading_service_metrics.h"
#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>

namespace proton {

/**
 * Metrics for a document db that are tagged with at least "documenttype":"name".
 * These tags are exposed as dimensions together with the metrics.
 */
struct DocumentDBTaggedMetrics : metrics::MetricSet
{
    struct JobMetrics : metrics::MetricSet
    {
        metrics::DoubleAverageMetric attributeFlush;
        metrics::DoubleAverageMetric memoryIndexFlush;
        metrics::DoubleAverageMetric diskIndexFusion;
        metrics::DoubleAverageMetric documentStoreFlush;
        metrics::DoubleAverageMetric documentStoreCompact;
        metrics::DoubleAverageMetric bucketMove;
        metrics::DoubleAverageMetric lidSpaceCompact;
        metrics::DoubleAverageMetric removedDocumentsPrune;
        metrics::DoubleAverageMetric total;

        JobMetrics(metrics::MetricSet *parent);
        ~JobMetrics();
    };

    struct SubDBMetrics : metrics::MetricSet
    {
        struct LidSpaceMetrics : metrics::MetricSet
        {
            metrics::LongValueMetric lidLimit;
            metrics::LongValueMetric usedLids;
            metrics::LongValueMetric lowestFreeLid;
            metrics::LongValueMetric highestUsedLid;
            metrics::DoubleValueMetric lidBloatFactor;
            metrics::DoubleValueMetric lidFragmentationFactor;

            LidSpaceMetrics(metrics::MetricSet *parent);
            ~LidSpaceMetrics();
        };

        struct DocumentStoreMetrics : metrics::MetricSet
        {
            metrics::LongValueMetric diskUsage;
            metrics::LongValueMetric diskBloat;
            metrics::DoubleValueMetric maxBucketSpread;
            MemoryUsageMetrics memoryUsage;

            DocumentStoreMetrics(metrics::MetricSet *parent);
            ~DocumentStoreMetrics();
        };

        LidSpaceMetrics lidSpace;
        DocumentStoreMetrics documentStore;
        proton::AttributeMetrics attributes;

        SubDBMetrics(const vespalib::string &name, metrics::MetricSet *parent);
        ~SubDBMetrics();
    };

    struct AttributeMetrics : metrics::MetricSet
    {
        struct ResourceUsageMetrics : metrics::MetricSet
        {
            metrics::DoubleValueMetric enumStore;
            metrics::DoubleValueMetric multiValue;
            metrics::LongValueMetric   feedingBlocked;

            ResourceUsageMetrics(metrics::MetricSet *parent);
            ~ResourceUsageMetrics();
        };

        ResourceUsageMetrics resourceUsage;

        AttributeMetrics(metrics::MetricSet *parent);
        ~AttributeMetrics();
    };

    struct IndexMetrics : metrics::MetricSet
    {
        metrics::LongValueMetric diskUsage;
        MemoryUsageMetrics memoryUsage;

        IndexMetrics(metrics::MetricSet *parent);
        ~IndexMetrics();
    };

    JobMetrics job;
    AttributeMetrics attribute;
    IndexMetrics index;
    SubDBMetrics ready;
    SubDBMetrics notReady;
    SubDBMetrics removed;
    ExecutorThreadingServiceMetrics threadingService;

    DocumentDBTaggedMetrics(const vespalib::string &docTypeName);
    ~DocumentDBTaggedMetrics();
};

} // namespace proton

