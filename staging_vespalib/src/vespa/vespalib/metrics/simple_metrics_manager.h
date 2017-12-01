// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <thread>
#include <vespa/vespalib/stllike/string.h>
#include "name_collection.h"
#include "current_samples.h"
#include "snapshots.h"
#include "metrics_manager.h"
#include "metric_types.h"
#include "clock.h"
#include "point_map_collection.h"
#include "bucket.h"
#include "ticker_thread.h"

namespace vespalib {
namespace metrics {

struct SimpleManagerConfig {
    int sliding_window_seconds;
    // possibly more config later
    SimpleManagerConfig() : sliding_window_seconds(60) {}
};


/**
 * Simple manager class that puts everything into a
 * single global repo with std::mutex locks used around
 * most operations.  Only implements sliding window
 * and a fixed (1 Hz) collecting interval.
 * XXX: Consider renaming this to "SlidingWindowManager".
 **/
class SimpleMetricsManager : public MetricsManager
{
private:
    NameCollection _metricNames;
    MetricTypes _metricTypes;
    NameCollection _dimensionNames;
    NameCollection _labelValues;
    PointMapCollection _pointMaps;

    const vespalib::string& nameFor(Dimension dimension) { return _dimensionNames.lookup(dimension.id()); }
    const vespalib::string& valueFor(Label label) { return _labelValues.lookup(label.id()); }

    CurrentSamples _currentBucket;

    InternalTimeStamp _startTime;
    InternalTimeStamp _curTime;

    std::mutex _bucketsLock;
    std::vector<Bucket> _buckets;
    size_t _firstBucket;
    size_t _maxBuckets;
    Bucket _totalsBucket;

    TickerThread _ticker;
    void collectCurrentBucket(); // called once per second from another thread
    Bucket mergeBuckets();
    Snapshot snapshotFrom(const Bucket &bucket);

    SimpleMetricsManager(const SimpleManagerConfig &config);
public:
    ~SimpleMetricsManager();
    static std::shared_ptr<MetricsManager> create(const SimpleManagerConfig &config);

    Counter counter(const vespalib::string &name) override;
    Gauge gauge(const vespalib::string &name) override;
    Dimension dimension(const vespalib::string &name) override;
    Label label(const vespalib::string &value) override;
    PointBuilder pointBuilder(Point from) override;
    Point pointFrom(PointMap::BackingMap map) override;
    Snapshot snapshot() override;
    Snapshot totalSnapshot() override;

    // for use from Counter only
    void add(Counter::Increment inc) override {
        _currentBucket.add(inc);
    }
    // for use from Gauge only
    void sample(Gauge::Measurement value) override {
        _currentBucket.sample(value);
    }

    void tick() { collectCurrentBucket(); }
};

} // namespace vespalib::metrics
} // namespace vespalib
