// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <thread>
#include <vespa/vespalib/stllike/string.h>
#include "simple_metrics.h"
#include "name_collection.h"
#include "mergers.h"
#include "snapshots.h"
#include "metrics_collector.h"
#include "clock.h"

namespace vespalib {
namespace metrics {

class DummyMetricsCollector : public MetricsCollector
{
private:
    InternalTimeStamp _startTime;
    DummyMetricsCollector() : _startTime(now_stamp()) {}
public:
    ~DummyMetricsCollector();

    static std::shared_ptr<MetricsCollector> create() {
        return std::shared_ptr<MetricsCollector>(new DummyMetricsCollector());
    }

    Counter counter(const vespalib::string &) override {
        return Counter(shared_from_this(), MetricIdentifier(0));
    }
    Gauge gauge(const vespalib::string &) override {
        return Gauge(shared_from_this(), MetricIdentifier(0));
    }

    Axis axis(const vespalib::string &) override {
        return Axis(0);
    }
    Coordinate coordinate(const vespalib::string &) override {
        return Coordinate(0);
    }
    Point origin() override { return Point(shared_from_this(), 0); }
    Point bind(const Point &, Axis, Coordinate) override {
        return Point(shared_from_this(), 0);
    }

    Snapshot snapshot() override;

    // for use from Counter only
    void add(CounterIncrement) override {}
    // for use from Gauge only
    void sample(GaugeMeasurement) override {}
};

} // namespace vespalib::metrics
} // namespace vespalib
