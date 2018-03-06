// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "executor_metrics.h"

namespace proton {

class ExecutorThreadingServiceStats;

/*
 * Metrics for executor threading service, i.e. tasks
 * accepted/rejected, queue len for each executor in a document db.
 */
struct ExecutorThreadingServiceMetrics : metrics::MetricSet
{
    ExecutorMetrics master;
    ExecutorMetrics index;
    ExecutorMetrics summary;
    ExecutorMetrics indexFieldInverter;
    ExecutorMetrics indexFieldWriter;
    ExecutorMetrics attributeFieldWriter;

    void update(const ExecutorThreadingServiceStats &stats);
    ExecutorThreadingServiceMetrics(const std::string &name, metrics::MetricSet *parent);
    ~ExecutorThreadingServiceMetrics();
};

}
