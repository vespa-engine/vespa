// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/valuemetric.h>

#include <vespa/fnet/connection.h>

namespace storage {

// Simple wrapper around low-level fnet network metrics
class FnetMetricsWrapper : public metrics::MetricSet
{
private:
    metrics::LongValueMetric _num_connections;

public:
    explicit FnetMetricsWrapper(metrics::MetricSet* owner);
    ~FnetMetricsWrapper() override;
    void update_metrics();
};

}
