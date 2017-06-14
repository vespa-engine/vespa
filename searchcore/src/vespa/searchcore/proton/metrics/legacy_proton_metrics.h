// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include "executor_metrics.h"

namespace proton {

/**
 * Metric set for all legacy metrics reported by proton.
 *
 * @deprecated Use ContentProtonMetrics for all new metrics.
 */
struct LegacyProtonMetrics : metrics::MetricSet
{
    struct DocumentTypeMetrics : metrics::MetricSet {
        // documentdb metrics will be wired in here (by the metrics engine)
        DocumentTypeMetrics(metrics::MetricSet *parent);
        ~DocumentTypeMetrics();
    };

    DocumentTypeMetrics                          docTypes;
    ExecutorMetrics                              executor;
    ExecutorMetrics                              flushExecutor;
    ExecutorMetrics                              matchExecutor;
    ExecutorMetrics                              summaryExecutor;
    metrics::SumMetric<metrics::LongValueMetric> memoryUsage;
    metrics::SumMetric<metrics::LongValueMetric> diskUsage;
    metrics::SumMetric<metrics::LongValueMetric> docsInMemory;
    metrics::SumMetric<metrics::LongValueMetric> numDocs;
    metrics::SumMetric<metrics::LongValueMetric> numActiveDocs;
    metrics::SumMetric<metrics::LongValueMetric> numIndexedDocs;
    metrics::SumMetric<metrics::LongValueMetric> numStoredDocs;
    metrics::SumMetric<metrics::LongValueMetric> numRemovedDocs;
    // transport metrics will be wired in here

    LegacyProtonMetrics();
    ~LegacyProtonMetrics();
};

} // namespace proton

