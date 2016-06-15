// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

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
        };

        struct DocumentStoreMetrics : metrics::MetricSet
        {
            metrics::LongValueMetric diskUsage;
            metrics::LongValueMetric diskBloat;
            metrics::DoubleValueMetric maxBucketSpread;

            DocumentStoreMetrics(metrics::MetricSet *parent);
        };

        LidSpaceMetrics lidSpace;
        DocumentStoreMetrics documentStore;

        SubDBMetrics(const vespalib::string &name, metrics::MetricSet *parent);
    };

    struct AttributeMetrics : metrics::MetricSet
    {
        struct ResourceUsageMetrics : metrics::MetricSet
        {
            metrics::DoubleValueMetric enumStore;
            metrics::DoubleValueMetric multiValue;
            metrics::LongValueMetric   feedingBlocked;

            ResourceUsageMetrics(metrics::MetricSet *parent);
        };

        ResourceUsageMetrics resourceUsage;

        AttributeMetrics(metrics::MetricSet *parent);
    };

    JobMetrics job;
    AttributeMetrics attribute;
    SubDBMetrics ready;
    SubDBMetrics notReady;
    SubDBMetrics removed;

    DocumentDBTaggedMetrics(const vespalib::string &docTypeName);
};

} // namespace proton

