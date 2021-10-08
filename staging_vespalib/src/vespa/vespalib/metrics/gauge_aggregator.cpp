// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "gauge_aggregator.h"
#include <assert.h>
#include <map>

namespace vespalib {
namespace metrics {

GaugeAggregator::GaugeAggregator(const Gauge::Measurement &sample)
    : idx(sample.idx),
      observedCount(1),
      sumValue(sample.value),
      minValue(sample.value),
      maxValue(sample.value),
      lastValue(sample.value)
{}

void
GaugeAggregator::merge(const GaugeAggregator &other)
{
    assert(idx == other.idx);
    minValue = std::min(minValue, other.minValue);
    maxValue = std::max(maxValue, other.maxValue);
    sumValue += other.sumValue;
    lastValue = other.lastValue;
    observedCount += other.observedCount;
}

} // namespace vespalib::metrics
} // namespace vespalib
