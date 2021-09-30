// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "executor_metrics.h"

namespace proton {

void
ExecutorMetrics::update(const vespalib::ThreadStackExecutorBase::Stats &stats)
{
    maxPending.set(stats.queueSize.max());
    accepted.inc(stats.acceptedTasks);
    rejected.inc(stats.rejectedTasks);
    const auto & qSize = stats.queueSize;
    queueSize.addValueBatch(qSize.average(), qSize.count(), qSize.min(), qSize.max());
}

ExecutorMetrics::ExecutorMetrics(const std::string &name, metrics::MetricSet *parent)
    : metrics::MetricSet(name, {}, "Instance specific thread executor metrics", parent),
      maxPending("maxpending", {}, "Maximum number of pending (active + queued) tasks", this),
      accepted("accepted", {}, "Number of accepted tasks", this),
      rejected("rejected", {}, "Number of rejected tasks", this),
      queueSize("queuesize", {}, "Size of task queue", this)
{
}

ExecutorMetrics::~ExecutorMetrics() = default;

} // namespace proton
