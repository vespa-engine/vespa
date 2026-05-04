// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/metrics/simple_metrics.h>
#include <vespa/vespalib/util/time.h>

#include <map>
#include <string>

namespace config::sentinel {

using vespalib::metrics::Counter;
using vespalib::metrics::Dimension;
using vespalib::metrics::Gauge;
using vespalib::metrics::MetricsManager;
using vespalib::metrics::Point;

struct StartMetrics {
    struct PerServiceRestarts {
        Point         point{Point::empty};
        unsigned long count{0};
    };

    std::shared_ptr<MetricsManager>              metrics;
    vespalib::metrics::Producer                  producer;
    unsigned long                                currentlyRunningServices;
    std::map<std::string, PerServiceRestarts>    totalRestartsByService;
    vespalib::steady_time                        startedTime;
    const Dimension                              service_dim;
    Counter                                      sentinel_restarts;
    Gauge                                        sentinel_totalRestarts;
    Gauge                                        sentinel_running;
    Gauge                                        sentinel_uptime;
    vespalib::steady_time                        lastRestartTime;

    StartMetrics();
    ~StartMetrics();

    void maybeLog();
    void incRestartsCounter(const std::string& serviceName);
    void reset();
};

} // namespace config::sentinel
