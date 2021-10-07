// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include "metric_id.h"
#include "point.h"

namespace vespalib {
namespace metrics {

class MetricsManager;
struct CounterAggregator;


/**
 * Represents a counter metric that can be incremented.
 **/
class Counter {
    std::shared_ptr<MetricsManager> _manager;
    MetricId _id;
public:
    Counter() : _manager(), _id(0) {}
    Counter(const Counter&) = delete;
    Counter(Counter &&other) = default;
    Counter& operator= (const Counter &) = delete;
    Counter& operator= (Counter &&other) = default;
    Counter(std::shared_ptr<MetricsManager> m, MetricId id)
        : _manager(std::move(m)), _id(id)
    {}

    // convenience methods:
    void add() const { add(1, Point::empty); }
    void add(Point p) { add(1, p); }
    void add(size_t count) const { add(count, Point::empty); }

    /**
     * Increment the counter.
     * @param count the amount to increment by (default 1)
     * @param p the point representing labels for this increment (default empty)
     **/
    void add(size_t count, Point p) const;

    // internal
    struct Increment {
        using Key = std::pair<MetricId, Point>;
        Key idx;
        size_t value;
        Increment() = delete;
        Increment(Key k, size_t v) : idx(k), value(v) {}
    };

    typedef CounterAggregator aggregator_type;
    typedef Increment sample_type;
};

} // namespace vespalib::metrics
} // namespace vespalib
