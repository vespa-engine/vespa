// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_metrics.h"

namespace proton {

void
ExecutorMetrics::update(const vespalib::ThreadStackExecutorBase::Stats &stats)
{
    maxPending.set(stats.maxPendingTasks);
    accepted.inc(stats.acceptedTasks);
    rejected.inc(stats.rejectedTasks);
}

ExecutorMetrics::ExecutorMetrics(const std::string &name, metrics::MetricSet *parent)
    : metrics::MetricSet(name, {}, "Instance specific thread executor metrics", parent),
      maxPending("maxpending", {}, "Maximum number of pending (active + queued) tasks", this),
      accepted("accepted", {}, "Number of accepted tasks", this),
      rejected("rejected", {}, "Number of rejected tasks", this)
{
}

ExecutorMetrics::~ExecutorMetrics() = default;

} // namespace proton
