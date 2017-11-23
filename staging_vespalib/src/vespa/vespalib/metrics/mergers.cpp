// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mergers.h"
#include <assert.h>
#include <map>

namespace vespalib {
namespace metrics {

CounterAggregator::CounterAggregator(MetricIdentifier id)
  : idx(id), count(0)
{}

void
CounterAggregator::merge(const CounterIncrement &increment)
{
    assert(idx == increment.idx);
    count += increment.value;
}

void
CounterAggregator::merge(const CounterAggregator &other)
{
    assert(idx == other.idx);
    count += other.count;
}


GaugeAggregator::GaugeAggregator(MetricIdentifier id)
  : idx(id),
    observedCount(0),
    sumValue(0.0),
    minValue(0.0),
    maxValue(0.0),
    lastValue(0.0)
{}

void
GaugeAggregator::merge(const GaugeMeasurement &other)
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

namespace {

template<typename T>
void
mergeWithMap(const NoReallocBunch<typename T::sample_type> &other,
             std::vector<typename T::aggregator_type> &result)
{
    using Aggregator = typename T::aggregator_type;
    using Sample = typename T::sample_type;
    using Map = std::map<MetricIdentifier, Aggregator>;
    using MapValue = typename Map::value_type;

    assert(result.size() == 0);
    Map map;
    other.apply([&map] (const Sample &sample) {
        MetricIdentifier id = sample.idx;
        if (map.find(id) == map.end()) {
            map.insert(MapValue(id, Aggregator(id)));
        }
        map.find(sample.idx)->second.merge(sample);
    });
    for (const MapValue &entry : map) {
        result.push_back(entry.second);
    }
}

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

void Bucket::merge(const CurrentSamples &other)
{
    mergeWithMap<Counter>(other.counterIncrements, counters);
    mergeWithMap<Gauge>(other.gaugeMeasurements, gauges);
}

void Bucket::merge(const Bucket &other)
{
    assert(startTime <= other.startTime);
    assert(endTime <= other.endTime);
    endTime = other.endTime;

    std::vector<CounterAggregator> nextCounters = mergeVectors(counters, other.counters);
    counters = std::move(nextCounters);

    std::vector<GaugeAggregator> nextGauges = mergeVectors(gauges, other.gauges);
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
