// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "gauge_aggregator.h"
#include <assert.h>
#include <map>

namespace vespalib {
namespace metrics {

GaugeAggregator::GaugeAggregator(MetricIdentifier id)
  : idx(id),
    observedCount(0),
    sumValue(0.0),
    minValue(0.0),
    maxValue(0.0),
    lastValue(0.0)
{}

void
GaugeAggregator::merge(const Gauge::Measurement &other)
{
    assert(idx == other.idx);
    if (observedCount == 0) {
        sumValue = other.value;
        minValue = other.value;
        maxValue = other.value;
    } else {
        sumValue += other.value;
        minValue = std::min(minValue, other.value);
        maxValue = std::max(maxValue, other.value);
    }
    lastValue = other.value;
    ++observedCount;
}

void
GaugeAggregator::merge(const GaugeAggregator &other)
{
    assert(idx == other.idx);
    if (observedCount == 0) {
        minValue = other.minValue;
        maxValue = other.maxValue;
    } else {
        minValue = std::min(minValue, other.minValue);
        maxValue = std::max(maxValue, other.maxValue);
    }
    sumValue += other.sumValue;
    lastValue = other.lastValue;
    observedCount += other.observedCount;
}

} // namespace vespalib::metrics
} // namespace vespalib
