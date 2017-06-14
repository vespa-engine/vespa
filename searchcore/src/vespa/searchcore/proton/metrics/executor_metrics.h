// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/metrics/metrics.h>
#include <vespa/vespalib/util/threadstackexecutorbase.h>

namespace proton {

struct ExecutorMetrics : metrics::MetricSet
{
    metrics::LongValueMetric maxPending;
    metrics::LongCountMetric accepted;
    metrics::LongCountMetric rejected;

    void update(const vespalib::ThreadStackExecutorBase::Stats &stats);
    ExecutorMetrics(const std::string &name, metrics::MetricSet *parent);
    ~ExecutorMetrics();
};

} // namespace proton

