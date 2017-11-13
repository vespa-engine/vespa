// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <chrono>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using clock = std::chrono::steady_clock;

class SimpleMetricsCollector;

class Gauge {
private:
    SimpleMetricsCollector &_manager;
    const int _idx;
public:
    Gauge(SimpleMetricsCollector &m, int idx) : _manager(m), _idx(idx) {}
    void sample(double value);
};

class Counter {
    SimpleMetricsCollector &_manager;
    const int _idx;
public:
    Counter(SimpleMetricsCollector &m, int idx) : _manager(m), _idx(idx) {}
    void add();
};

struct CounterIncrement {
    int idx;
};

struct GaugeMeasurement {
    int idx;
    double value;
};

class Bucket {
public:
    clock::time_point startTime();
    clock::time_point endedTime();
};

struct MergedCounter {
    int idx;
    size_t count;
    MergedCounter(int idx);
    void merge(const CounterIncrement &other);
    void merge(const MergedCounter &other);
};

struct CounterSnapshot {
    const vespalib::string &name;
    const size_t count;
    CounterSnapshot(const vespalib::string &n, const MergedCounter &c)
        : name(n), count(c.count)
    {}
};

struct MergedGauge {
    int idx;
    size_t observedCount;
    double sumValue;
    double minValue;
    double maxValue;
    double lastValue;
    MergedGauge(int idx);
    void merge(const GaugeMeasurement &other);
    void merge(const MergedGauge &other);
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
    clock::time_point startTime();
    clock::time_point endedTime();

    std::vector<CounterSnapshot> counters() const;
    std::vector<GaugeSnapshot> gauges() const;
};

} // namespace vespalib::metrics
} // namespace vespalib
