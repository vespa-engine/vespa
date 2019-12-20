// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/metrics/simple_metrics.h>
#include <vespa/vespalib/util/time.h>

namespace config::sentinel {

using vespalib::metrics::Counter;
using vespalib::metrics::Gauge;
using vespalib::metrics::MetricsManager;

struct StartMetrics {
    std::shared_ptr<MetricsManager> metrics;
    vespalib::metrics::Producer producer;
    unsigned long currentlyRunningServices;
    unsigned long totalRestartsCounter;
    vespalib::steady_time startedTime;
    Counter sentinel_restarts;
    Gauge   sentinel_totalRestarts;
    Gauge   sentinel_running;
    Gauge   sentinel_uptime;

    StartMetrics();
    ~StartMetrics();

    void maybeLog();
};

}
