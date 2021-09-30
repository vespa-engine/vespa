// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metricset.h>
#include <vespa/metrics/countmetric.h>
#include <vespa/metrics/valuemetric.h>
#include <vespa/vespalib/util/threadstackexecutorbase.h>

namespace proton {

struct ExecutorMetrics : metrics::MetricSet
{
    metrics::LongValueMetric maxPending; // TODO Remove on Vespa 8 or sooner if possible.
    metrics::LongCountMetric accepted;
    metrics::LongCountMetric rejected;
    metrics::LongAverageMetric queueSize;

    void update(const vespalib::ThreadStackExecutorBase::Stats &stats);
    ExecutorMetrics(const std::string &name, metrics::MetricSet *parent);
    ~ExecutorMetrics();
};

} // namespace proton

