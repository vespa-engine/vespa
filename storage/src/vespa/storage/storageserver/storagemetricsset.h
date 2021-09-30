// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tls_statistics_metrics_wrapper.h"
#include "fnet_metrics_wrapper.h"

namespace storage {

class MessageMemoryUseMetricSet : public metrics::MetricSet
{
public:
    metrics::LongValueMetric total;
    metrics::LongValueMetric lowpri;
    metrics::LongValueMetric normalpri;
    metrics::LongValueMetric highpri;
    metrics::LongValueMetric veryhighpri;

    explicit MessageMemoryUseMetricSet(metrics::MetricSet* owner);
    ~MessageMemoryUseMetricSet() override;
};

struct StorageMetricSet : public metrics::MetricSet
{
    metrics::LongValueMetric memoryUse;
    MessageMemoryUseMetricSet memoryUse_messages;
    metrics::LongValueMetric memoryUse_visiting;

    TlsStatisticsMetricsWrapper tls_metrics;
    FnetMetricsWrapper fnet_metrics;

    StorageMetricSet();
    ~StorageMetricSet() override;
    void updateMetrics();
};

} // storage

