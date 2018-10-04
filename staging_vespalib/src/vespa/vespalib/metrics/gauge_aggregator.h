// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "metric_point_id.h"
#include "gauge.h"

namespace vespalib {
namespace metrics {

// internal
struct GaugeAggregator {
    MetricPointId idx;
    size_t observedCount;
    double sumValue;
    double minValue;
    double maxValue;
    double lastValue;

    GaugeAggregator(const Gauge::Measurement &other);
    void merge(const GaugeAggregator &other);
};

} // namespace vespalib::metrics
} // namespace vespalib
