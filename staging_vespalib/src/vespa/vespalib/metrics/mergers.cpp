// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mergers.h"
#include <assert.h>

namespace vespalib {
namespace metrics {

MergedCounter::MergedCounter(size_t id)
  : idx(id), count(0)
{}

void
MergedCounter::merge(const CounterIncrement &increment)
{
    assert(idx == increment.idx);
    count += increment.value;
}

void
MergedCounter::merge(const MergedCounter &other)
{
    assert(idx == other.idx);
    count += other.count;
}


MergedGauge::MergedGauge(size_t id)
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
MergedGauge::merge(const MergedGauge &other)
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

void Bucket::merge(const CurrentSamples &other)
{
    for (CounterIncrement inc : other.counterIncrements) {
        while (counters.size() <= inc.idx) {
            size_t id = counters.size();
            counters.emplace_back(id);
        }
        counters[inc.idx].merge(inc);
    }
    for (GaugeMeasurement sample : other.gaugeMeasurements) {
        while (gauges.size() <= sample.idx) {
            size_t id = gauges.size();
            gauges.emplace_back(id);
        }
        gauges[sample.idx].merge(sample);
    }
}

void Bucket::merge(const Bucket &other)
{
    assert(startTime <= other.startTime);
    assert(endTime <= other.endTime);
    endTime = other.endTime;
    while (counters.size() < other.counters.size()) {
        size_t id = counters.size();
        counters.emplace_back(id);
    }
    for (const MergedCounter & entry : other.counters) {
        counters[entry.idx].merge(entry);
    }
    while (gauges.size() < other.gauges.size()) {
        size_t id = gauges.size();
        gauges.emplace_back(id);
    }
    for (const MergedGauge & entry : other.gauges) {
        gauges[entry.idx].merge(entry);
    }
}

void swap(CurrentSamples& a, CurrentSamples& b)
{
    using std::swap;
    swap(a.counterIncrements, b.counterIncrements);
    swap(a.gaugeMeasurements, b.gaugeMeasurements);
}

void swap(Bucket& a, Bucket& b)
{
    using std::swap;
    swap(a.startTime, b.startTime);
    swap(a.endTime, b.endTime);
    swap(a.counters, b.counters);
    swap(a.gauges, b.gauges);
}

} // namespace vespalib::metrics
} // namespace vespalib
