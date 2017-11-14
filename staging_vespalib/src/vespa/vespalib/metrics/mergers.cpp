// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mergers.h"

namespace vespalib {
namespace metrics {

MergedCounter::MergedCounter(int id)
  : idx(id), count(0)
{}

void
MergedCounter::merge(const CounterIncrement &)
{
    ++count;
}

void
MergedCounter::merge(const MergedCounter &other)
{
    count += other.count;
}


MergedGauge::MergedGauge(int id)
  : idx(id),
    observedCount(0),
    sumValue(0.0),
    minValue(0.0),
    maxValue(0.0),
    lastValue(0.0)
{}

void
MergedGauge::merge(const GaugeMeasurement &other)
{
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
MergedGauge::merge(const MergedGauge &other)
{
    // NB assumes (other.obsevedCount > 0)
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
