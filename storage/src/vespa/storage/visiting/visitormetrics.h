// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::VisitorMetrics
 * @ingroup visiting
 *
 * @brief Metrics for visiting.
 *
 * @version $Id$
 */
#pragma once

#include <vespa/metrics/metrics.h>
#include "visitorthreadmetrics.h"

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
    ~VisitorMetrics();

    void initThreads(uint16_t threadCount, const metrics::LoadTypeSet& loadTypes);
};

} // storage

