// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>

namespace proton {

/*
 * Metrics for commits during feeding within a document db.
 */
struct DocumentDBCommitMetrics : metrics::MetricSet
{
    metrics::DoubleAverageMetric operations;
    metrics::DoubleAverageMetric latency;

    DocumentDBCommitMetrics(metrics::MetricSet* parent);
    ~DocumentDBCommitMetrics() override;
};

}
