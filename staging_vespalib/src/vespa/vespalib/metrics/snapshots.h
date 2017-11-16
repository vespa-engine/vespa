// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_metrics.h"
#include "mergers.h"

namespace vespalib {
namespace metrics {

class CounterSnapshot {
private:
    const vespalib::string &_name;
    const size_t _count;
public:
    CounterSnapshot(const vespalib::string &n, const MergedCounter &c)
        : _name(n), _count(c.count)
    {}
    const vespalib::string &name() const { return _name; }
    size_t count() const { return _count; }
};

class GaugeSnapshot {
private:
    const vespalib::string &_name;
    const size_t _observedCount;
    const double _averageValue;
    const double _minValue;
    const double _maxValue;
    const double _lastValue;
public:
    GaugeSnapshot(const vespalib::string &n, const MergedGauge &c)
        : _name(n),
          _observedCount(c.observedCount),
          _averageValue(c.sumValue / c.observedCount),
          _minValue(c.minValue),
          _maxValue(c.maxValue),
          _lastValue(c.lastValue)
    {}
    const vespalib::string &name() const { return _name; }
    size_t observedCount() const { return _observedCount; }
    double averageValue() const { return _averageValue; }
    double minValue() const { return _minValue; }
    double maxValue() const { return _maxValue; }
    double lastValue() const { return _lastValue; }
};

class Snapshot {
public:
    double startTime(); // seconds since 1970
    double endTime(); // seconds since 1970

    const std::vector<CounterSnapshot> &counters() const;
    const std::vector<GaugeSnapshot> &gauges() const;
};

} // namespace vespalib::metrics
} // namespace vespalib
