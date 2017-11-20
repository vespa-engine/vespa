// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simple_metrics.h"

namespace vespalib {
namespace metrics {

class MetricsCollector;
class MergedCounter;

struct CounterIncrement {
    MetricIdentifier idx;
    size_t value;
    CounterIncrement() = delete;
    CounterIncrement(MetricIdentifier id, size_t v) : idx(id), value(v) {}
};

class Counter {
    std::shared_ptr<MetricsCollector> _manager;
    MetricIdentifier _idx;
public:
    Counter() : _manager(), _idx() {}
    Counter(const Counter&) = delete;
    Counter(Counter &&other) = default;
    Counter& operator= (const Counter &) = delete;
    Counter& operator= (Counter &&other) = default;
    Counter(std::shared_ptr<MetricsCollector> m, MetricIdentifier id) : _manager(m), _idx(id) {}

    void add();
    void add(size_t count);

    MetricIdentifier id() const { return _idx; }

    typedef MergedCounter aggregator_type;
    typedef CounterIncrement sample_type;
};

} // namespace vespalib::metrics
} // namespace vespalib
