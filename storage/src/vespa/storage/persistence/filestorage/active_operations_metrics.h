// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>

namespace storage {

/*
 * Metrics for active operations with bucket lock at service layer.
 */
struct ActiveOperationsMetrics : public metrics::MetricSet
{
    metrics::DoubleAverageMetric size;
    metrics::DoubleAverageMetric latency;

    ActiveOperationsMetrics(metrics::MetricSet* parent);
    ~ActiveOperationsMetrics() override;
};

}
