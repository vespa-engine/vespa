// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_metrics.h"
#include "name_collection.h"
#include "mergers.h"
#include "snapshots.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

struct CollectorConfig {
    int sliding_window_seconds;
    // possibly more config later
};

class SimpleMetricsCollector {
private:
    NameCollection _counterNames;
    NameCollection _gaugeNames;
    CurrentSamples _currentBucket;

    clock::time_point _startTime;
    clock::time_point _curTime;

    std::vector<Bucket> _buckets;
    size_t _firstBucket;
    size_t _maxBuckets;
    // lots of stuff

public:
    SimpleMetricsCollector(const CollectorConfig &config);

    void collectCurrentBucket();

    Counter declareCounter(const vespalib::string &name);
    Gauge declareGauge(const vespalib::string &name);

    Snapshot getSnapshot();
};

} // namespace vespalib::metrics
} // namespace vespalib
