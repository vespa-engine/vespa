// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mergers.h"
#include <assert.h>
#include <map>

namespace vespalib {
namespace metrics {

MergedCounter::MergedCounter(MetricIdentifier id)
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


MergedGauge::MergedGauge(MetricIdentifier id)
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

void Bucket::mergeCounters(const NoReallocBunch<CounterIncrement> &other)
{
    assert(counters.size() == 0);
    using Map = std::map<MetricIdentifier, MergedCounter>;
    Map map;
    other.apply([&map] (const CounterIncrement &inc) {
        MetricIdentifier id = inc.idx;
        if (map.find(id) == map.end()) {
            map.insert(Map::value_type(id, MergedCounter(id)));
        }
        map.find(id)->second.merge(inc);
    });
    for (const Map::value_type &entry : map) {
        counters.push_back(entry.second);
    }
}

void Bucket::mergeGauges(const NoReallocBunch<GaugeMeasurement> &other)
{
    assert(gauges.size() == 0);
    using Map = std::map<MetricIdentifier, MergedGauge>;
    Map map;
    other.apply([&map] (const GaugeMeasurement &sample) {
        MetricIdentifier id = sample.idx;
        if (map.find(id) == map.end()) {
            map.insert(Map::value_type(id, MergedGauge(id)));
        }
        map.find(sample.idx)->second.merge(sample);
    });
    for (const Map::value_type &entry : map) {
        gauges.push_back(entry.second);
    }
}

void Bucket::merge(const CurrentSamples &other)
{
    mergeCounters(other.counterIncrements);
    mergeGauges(other.gaugeMeasurements);
}


namespace {

template<typename T>
std::vector<T>
mergeVectors(const std::vector<T> &a,
             const std::vector<T> &b)
{
    std::vector<T> result;
    auto a_iter = a.begin();
    auto b_iter = b.begin();
    while (a_iter != a.end() &&
           b_iter != b.end())
    {
        if (a_iter->idx < b_iter->idx) {
            result.push_back(*a_iter);
            ++a_iter;
        } else if (b_iter->idx < a_iter->idx) {
            result.push_back(*b_iter);
            ++b_iter;
        } else {
            T both = *a_iter;
            both.merge(*b_iter);
            result.push_back(both);
            ++a_iter;
            ++b_iter;
        }
    }
    while (a_iter != a.end()) {
        result.push_back(*a_iter);
        ++a_iter;
    }
    while (b_iter != b.end()) {
        result.push_back(*b_iter);
        ++b_iter;
    }
    return result;
}

} // namespace <unnamed>

void Bucket::merge(const Bucket &other)
{
    assert(startTime <= other.startTime);
    assert(endTime <= other.endTime);
    endTime = other.endTime;

    std::vector<MergedCounter> nextCounters = mergeVectors(counters, other.counters);
    counters = std::move(nextCounters);

    std::vector<MergedGauge> nextGauges = mergeVectors(gauges, other.gauges);
    gauges = std::move(nextGauges);
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
