// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "gauge.h"
#include "metric_id.h"
#include "point.h"

namespace vespalib {
namespace metrics {

// internal
struct GaugeAggregator {
    std::pair<MetricId, Point> idx;
    size_t                     observedCount;
    double                     sumValue;
    double                     minValue;
    double                     maxValue;
    double                     lastValue;

    GaugeAggregator(const Gauge::Measurement& other);
    void merge(const GaugeAggregator& other);
};

} // namespace metrics
} // namespace vespalib
