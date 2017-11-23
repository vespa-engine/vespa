// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metric_identifier.h"
#include "point.h"

namespace vespalib {
namespace metrics {

class MetricsCollector;
class CounterAggregator;

struct CounterIncrement {
    MetricIdentifier idx;
    size_t value;
    CounterIncrement() = delete;
    CounterIncrement(MetricIdentifier id, size_t v) : idx(id), value(v) {}
};

class Counter {
    std::shared_ptr<MetricsCollector> _manager;
    MetricIdentifier _idx;
    MetricIdentifier ident() const { return _idx; }
public:
    Counter() : _manager(), _idx() {}
    Counter(const Counter&) = delete;
    Counter(Counter &&other) = default;
    Counter& operator= (const Counter &) = delete;
    Counter& operator= (Counter &&other) = default;
    Counter(std::shared_ptr<MetricsCollector> &&m, MetricIdentifier id)
        : _manager(std::move(m)), _idx(id)
    {}

    void add() const;
    void add(size_t count) const;
    void add(Point p) const;
    void add(size_t count, Point p) const;

    typedef CounterAggregator aggregator_type;
    typedef CounterIncrement sample_type;
};

} // namespace vespalib::metrics
} // namespace vespalib
