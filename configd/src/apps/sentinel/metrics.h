// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <sys/time.h>

namespace config {
namespace sentinel {

struct StartMetrics {
    unsigned long currentlyRunningServices;
    unsigned long totalRestartsCounter;
    unsigned long totalRestartsLastPeriod;
    long lastLoggedTime;
    unsigned long totalRestartsLastSnapshot;
    long snapshotStart;
    long snapshotEnd;
    long startedTime;

    StartMetrics();

    void output();
    void reset(unsigned long curTime);
    void maybeLog();
};

} // end namespace config::sentinel
} // end namespace config

