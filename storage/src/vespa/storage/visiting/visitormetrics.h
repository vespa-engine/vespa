// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "visitorthreadmetrics.h"
#include <vespa/metrics/summetric.h>
#include <vespa/metrics/countmetric.h>

namespace storage {

struct VisitorMetrics : public metrics::MetricSet
{
    metrics::LongAverageMetric queueSize;
    metrics::LongCountMetric queueSkips;
    metrics::LongCountMetric queueFull;
    metrics::DoubleAverageMetric queueWaitTime;
    metrics::DoubleAverageMetric queueTimeoutWaitTime;
    metrics::DoubleAverageMetric queueEvictedWaitTime;
    std::vector<std::shared_ptr<VisitorThreadMetrics> > threads;
    metrics::SumMetric<MetricSet> sum;

    VisitorMetrics();
    ~VisitorMetrics() override;

    void initThreads(uint16_t threadCount);
};

} // storage

