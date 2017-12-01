// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metric_identifier.h"
#include "counter.h"

namespace vespalib {
namespace metrics {

// internal
struct CounterAggregator {
    MetricIdentifier idx;
    size_t count;

    CounterAggregator(MetricIdentifier id);

    void merge(const Counter::Increment &other);
    void merge(const CounterAggregator &other);
};

} // namespace vespalib::metrics
} // namespace vespalib
