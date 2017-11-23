// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <thread>
#include <vespa/vespalib/stllike/string.h>
#include "name_collection.h"
#include "mergers.h"
#include "snapshots.h"
#include "metrics_manager.h"
#include "metric_types.h"
#include "clock.h"

namespace vespalib {
namespace metrics {

struct SimpleManagerConfig {
    int sliding_window_seconds;
    // possibly more config later
    SimpleManagerConfig() : sliding_window_seconds(60) {}
};


class SimpleMetricsManager : public MetricsManager
{
private:
    NameCollection _metricNames;
    MetricTypes _metricTypes;
    NameCollection _dimensionNames;
    NameCollection _labelValues;
    using PointMapMap = std::map<PointMap, size_t>;
    struct {
        std::mutex lock;
        PointMapMap map;
        std::vector<PointMapMap::const_iterator> vec;
    } _pointMaps;

    const vespalib::string& nameFor(Dimension dimension) { return _dimensionNames.lookup(dimension.id()); }
    const vespalib::string& valueFor(Label label) { return _labelValues.lookup(label.id()); }

    CurrentSamples _currentBucket;

    InternalTimeStamp _startTime;
    InternalTimeStamp _curTime;

    std::vector<Bucket> _buckets;
    size_t _firstBucket;
    size_t _maxBuckets;
    // lots of stuff

    bool _stopFlag;
    std::thread _collectorThread;
    static void doCollectLoop(SimpleMetricsManager *me);
    void collectCurrentBucket(); // called once per second from another thread

    SimpleMetricsManager(const SimpleManagerConfig &config);
public:
    ~SimpleMetricsManager();
    static std::shared_ptr<MetricsManager> create(const SimpleManagerConfig &config);

    Counter counter(const vespalib::string &name) override; // get or create
    Gauge gauge(const vespalib::string &name) override; // get or create

    Dimension dimension(const vespalib::string &name) override;
    Label label(const vespalib::string &value) override;
    PointBuilder pointBuilder(Point from) override;
    Point pointFrom(PointMapBacking &&map) override;

    Snapshot snapshot() override;

    // for use from Counter only
    void add(CounterIncrement inc) override {
        _currentBucket.add(inc);
    }
    // for use from Gauge only
    void sample(GaugeMeasurement value) override {
        _currentBucket.sample(value);
    }
};

} // namespace vespalib::metrics
} // namespace vespalib
