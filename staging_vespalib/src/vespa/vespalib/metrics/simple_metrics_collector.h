// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_metrics.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

struct CollectorConfig {
    int sliding_window_seconds;
    // possibly more config later
};

class NameCollection {
private:
    std::vector<vespalib::string> _names;
public:
    const vespalib::string &lookup(int idx) const;
    int lookup(const vespalib::string& name) const;
    int resolve(const vespalib::string& name);
};

class SimpleMetricsCollector {
private:
    NameCollection _counterNames;
    NameCollection _gaugeNames;

    struct CurrentSamples {
        std::vector<CounterIncrement> counterIncrements;
        std::vector<GaugeMeasurement > gaugeMeasurements;
    } _currentBucket;

    std::vector<Bucket> _buckets;
    // lots of stuff
public:
    SimpleMetricsCollector(const CollectorConfig &config);

    Counter declareCounter(const vespalib::string &name);
    Gauge declareGauge(const vespalib::string &name);

    Snapshot getSnapshot();
};

} // namespace vespalib::metrics
} // namespace vespalib
