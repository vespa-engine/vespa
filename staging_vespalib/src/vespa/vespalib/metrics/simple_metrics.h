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

} // namespace vespalib::metrics
} // namespace vespalib
