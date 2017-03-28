// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "metrics.h"
#include <vespa/log/log.h>
LOG_SETUP(".metrics");

namespace config {
namespace sentinel {

StartMetrics::StartMetrics()
    : currentlyRunningServices(0), totalRestartsCounter(0), totalRestartsLastPeriod(0),
      lastLoggedTime(0),
      totalRestartsLastSnapshot(0),
      snapshotStart(0),
      snapshotEnd(0)
{
    snapshotEnd = time(nullptr);
    lastLoggedTime = snapshotEnd - 55;
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
    totalRestartsLastSnapshot = totalRestartsLastPeriod;
    snapshotStart = snapshotEnd;
    snapshotEnd = curTime;
    totalRestartsLastPeriod = 0;
    lastLoggedTime = curTime;
}

void
StartMetrics::maybeLog()
{
    uint32_t curTime = time(nullptr);
    if (curTime > lastLoggedTime + 59) {
        output();
        reset(curTime);
    }
}

} // end namespace config::sentinel
} // end namespace config
