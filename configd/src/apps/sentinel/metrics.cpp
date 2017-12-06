// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics.h"
#include <vespa/log/log.h>
LOG_SETUP(".metrics");

#include <vespa/vespalib/metrics/simple_metrics.h>

namespace config {
namespace sentinel {

using vespalib::metrics::SimpleMetricsManager;
using vespalib::metrics::SimpleManagerConfig;

StartMetrics::StartMetrics()
    : metrics(SimpleMetricsManager::create(SimpleManagerConfig())),
      producer(metrics),
      currentlyRunningServices(0),
      totalRestartsCounter(0),
      totalRestartsLastPeriod(1),
      startedTime(time(nullptr)),
      lastLoggedTime(startedTime - 55),
      sentinel_restarts(metrics->counter("sentinel.restarts",
              "how many times sentinel restarted a service")),
      sentinel_totalRestarts(metrics->gauge("sentinel.totalRestarts",
              "how many times sentinel restarted a service since sentinel start")),
      sentinel_running(metrics->gauge("sentinel.running",
              "how many services the sentinel has running currently")),
      sentinel_uptime(metrics->gauge("sentinel.uptime",
              "how many seconds has the sentinel been running"))
{
    // account for the sentinel itself restarting
    sentinel_restarts.add();
}

void
StartMetrics::output()
{
    EV_VALUE("currently_running_services", currentlyRunningServices);
    EV_VALUE("total_restarts_last_period", totalRestartsLastPeriod);
    EV_COUNT("total_restarts_counter", totalRestartsCounter);
}

void
StartMetrics::reset(unsigned long curTime)
{
    totalRestartsLastPeriod = 0;
    lastLoggedTime = curTime;
}

void
StartMetrics::maybeLog()
{
    uint32_t curTime = time(nullptr);
    sentinel_totalRestarts.sample(totalRestartsCounter);
    sentinel_running.sample(currentlyRunningServices);
    sentinel_uptime.sample(curTime - startedTime);
    if (curTime > lastLoggedTime + 59) {
        output();
        reset(curTime);
    }
}

} // end namespace config::sentinel
} // end namespace config
