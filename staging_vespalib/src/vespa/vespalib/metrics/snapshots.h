// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_metrics.h"
#include "mergers.h"

namespace vespalib {
namespace metrics {

struct CounterSnapshot {
    const vespalib::string &name;
    const size_t count;
    CounterSnapshot(const vespalib::string &n, const MergedCounter &c)
        : name(n), count(c.count)
    {}
};

struct GaugeSnapshot {
    const vespalib::string &name;
    const size_t observedCount;
    const double averageValue;
    const double minValue;
    const double maxValue;
    const double lastValue;
    GaugeSnapshot(const vespalib::string &n, const MergedGauge &c)
        : name(n),
          observedCount(c.observedCount),
          averageValue(c.sumValue / c.observedCount),
          minValue(c.minValue),
          maxValue(c.maxValue),
          lastValue(c.lastValue)
    {}
};

class Snapshot {
public:
    double startTime(); // seconds since 1970
    double endedTime(); // seconds since 1970

    std::vector<CounterSnapshot> counters() const;
    std::vector<GaugeSnapshot> gauges() const;
};

} // namespace vespalib::metrics
} // namespace vespalib
