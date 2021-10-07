// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include "counter_aggregator.h"
#include "gauge_aggregator.h"

namespace vespalib::metrics {

class DimensionBinding {
private:
    const vespalib::string _dimensionName;
    const vespalib::string _labelValue;
public:
    const vespalib::string &dimensionName() const { return _dimensionName; }
    const vespalib::string &labelValue() const { return _labelValue; }
    DimensionBinding(const vespalib::string &a,
                     const vespalib::string &v) noexcept
        : _dimensionName(a), _labelValue(v)
    {}
    ~DimensionBinding() {}
};

struct PointSnapshot {
    std::vector<DimensionBinding> dimensions;
};

class CounterSnapshot {
private:
    const vespalib::string _name;
    const PointSnapshot &_point;
    const size_t _count;
public:
    CounterSnapshot(const vespalib::string &n, const PointSnapshot &p, const CounterAggregator &c)
        : _name(n), _point(p), _count(c.count)
    {}
    ~CounterSnapshot() {}
    const vespalib::string &name() const { return _name; }
    const PointSnapshot &point() const { return _point; }
    size_t count() const { return _count; }
};

class GaugeSnapshot {
private:
    const vespalib::string _name;
    const PointSnapshot &_point;
    const size_t _observedCount;
    const double _averageValue;
    const double _sumValue;
    const double _minValue;
    const double _maxValue;
    const double _lastValue;
public:
    GaugeSnapshot(const vespalib::string &n, const PointSnapshot &p, const GaugeAggregator &c)
        : _name(n),
          _point(p),
          _observedCount(c.observedCount),
          _averageValue(c.sumValue / (c.observedCount > 0 ? c.observedCount : 1)),
          _sumValue(c.sumValue),
          _minValue(c.minValue),
          _maxValue(c.maxValue),
          _lastValue(c.lastValue)
    {}
    ~GaugeSnapshot() {}
    const vespalib::string &name() const { return _name; }
    const PointSnapshot &point() const { return _point; }
    size_t observedCount() const { return _observedCount; }
    double averageValue() const { return _averageValue; }
    double sumValue() const { return _sumValue; }
    double minValue() const { return _minValue; }
    double maxValue() const { return _maxValue; }
    double lastValue() const { return _lastValue; }
};

class Snapshot {
private:
    double _start;
    double _end;
    std::vector<CounterSnapshot> _counters;
    std::vector<GaugeSnapshot> _gauges;
    std::vector<PointSnapshot> _points;
public:
    double startTime() const { return _start; }; // seconds since 1970
    double endTime()   const { return _end; };   // seconds since 1970

    const std::vector<CounterSnapshot> &counters() const {
        return _counters;
    }
    const std::vector<GaugeSnapshot> &gauges() const {
        return _gauges;
    }
    const std::vector<PointSnapshot> &points() const {
        return _points;
    }

    // builders:
    Snapshot(double s, double e)
        : _start(s), _end(e), _counters(), _gauges()
    {}
    ~Snapshot() {}
    void add(const PointSnapshot &entry)   { _points.push_back(entry); }
    void add(const CounterSnapshot &entry) { _counters.push_back(entry); }
    void add(const GaugeSnapshot &entry)   { _gauges.push_back(entry); }
};

}
