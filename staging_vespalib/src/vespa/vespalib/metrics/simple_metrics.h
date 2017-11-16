// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <chrono>
#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using clock = std::chrono::steady_clock;

class SimpleMetricsCollector;

class Gauge {
private:
    std::shared_ptr<SimpleMetricsCollector> _manager;
    const size_t _idx;
public:
    Gauge(std::shared_ptr<SimpleMetricsCollector> m, int idx) : _manager(m), _idx(idx) {}
    void sample(double value);
};

class Counter {
    std::shared_ptr<SimpleMetricsCollector> _manager;
    const size_t _idx;
public:
    Counter(std::shared_ptr<SimpleMetricsCollector> m, int idx) : _manager(m), _idx(idx) {}
    void add();
};

struct CounterIncrement {
    size_t idx;
    CounterIncrement() = delete;
    explicit CounterIncrement(size_t id) : idx(id) {}
};

struct GaugeMeasurement {
    size_t idx;
    double value;
    GaugeMeasurement() = delete;
    GaugeMeasurement(size_t id, double v) : idx(id), value(v) {}
};

} // namespace vespalib::metrics
} // namespace vespalib
