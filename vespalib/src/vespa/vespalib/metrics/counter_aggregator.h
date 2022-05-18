// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metric_id.h"
#include "point.h"
#include "counter.h"

namespace vespalib {
namespace metrics {

// internal
struct CounterAggregator {
    std::pair<MetricId, Point> idx;
    size_t count;

    CounterAggregator(const Counter::Increment &other);
    void merge(const CounterAggregator &other);
};

} // namespace vespalib::metrics
} // namespace vespalib
