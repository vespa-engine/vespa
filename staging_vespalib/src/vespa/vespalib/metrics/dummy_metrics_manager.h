// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <thread>
#include <vespa/vespalib/stllike/string.h>
#include "name_collection.h"
#include "mergers.h"
#include "snapshots.h"
#include "metrics_manager.h"
#include "clock.h"

namespace vespalib {
namespace metrics {

class DummyMetricsManager : public MetricsManager
{
private:
    InternalTimeStamp _startTime;
    DummyMetricsManager() : _startTime(now_stamp()) {}
public:
    ~DummyMetricsManager();

    static std::shared_ptr<MetricsManager> create() {
        return std::shared_ptr<MetricsManager>(new DummyMetricsManager());
    }

    Counter counter(const vespalib::string &) override {
        return Counter(shared_from_this(), MetricIdentifier(0));
    }
    Gauge gauge(const vespalib::string &) override {
        return Gauge(shared_from_this(), MetricIdentifier(0));
    }

    Dimension dimension(const vespalib::string &) override {
        return Dimension(0);
    }
    Label label(const vespalib::string &) override {
        return Label(0);
    }
    PointBuilder pointBuilder(Point) override {
        return PointBuilder(shared_from_this());
    }
    Point pointFrom(PointMapBacking &&) override {
        return Point(0);
    }

    Snapshot snapshot() override;

    // for use from Counter only
    void add(CounterIncrement) override {}
    // for use from Gauge only
    void sample(GaugeMeasurement) override {}
};

} // namespace vespalib::metrics
} // namespace vespalib
