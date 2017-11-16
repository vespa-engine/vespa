// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <thread>
#include <vespa/vespalib/stllike/string.h>
#include "simple_metrics.h"
#include "name_collection.h"
#include "mergers.h"
#include "snapshots.h"

namespace vespalib {
namespace metrics {

struct CollectorConfig {
    int sliding_window_seconds;
    // possibly more config later
};


class SimpleMetricsCollector
    : public std::enable_shared_from_this<SimpleMetricsCollector>
{
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

    bool _stopFlag;
    std::thread _collectorThread;
    static void doCollectLoop(SimpleMetricsCollector *me);
    void collectCurrentBucket(); // called once per second from another thread

    SimpleMetricsCollector(const CollectorConfig &config);
public:
    ~SimpleMetricsCollector();
    static std::shared_ptr<SimpleMetricsCollector> create(const CollectorConfig &config);

    Counter counter(const vespalib::string &name); // get or create
    Gauge gauge(const vespalib::string &name); // get or create

    Snapshot getSnapshot();

    // for use from Counter only
    void add(CounterIncrement inc) {
        _currentBucket.add(inc);
    }
    // for use from Gauge only
    void sample(GaugeMeasurement value) {
        _currentBucket.sample(value);
    }
};

} // namespace vespalib::metrics
} // namespace vespalib
