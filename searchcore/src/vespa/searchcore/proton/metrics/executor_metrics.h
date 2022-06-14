// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/vespalib/util/executor_stats.h>

namespace proton {

struct ExecutorMetrics : metrics::MetricSet
{
    metrics::LongCountMetric   accepted;
    metrics::LongCountMetric   rejected;
    metrics::LongCountMetric   wakeupCount;
    metrics::DoubleValueMetric util;
    metrics::LongAverageMetric queueSize;

    void update(const vespalib::ExecutorStats &stats);
    ExecutorMetrics(const std::string &name, metrics::MetricSet *parent);
    ~ExecutorMetrics();
};

} // namespace proton

