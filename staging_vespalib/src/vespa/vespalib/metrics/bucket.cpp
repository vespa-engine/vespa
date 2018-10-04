// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucket.h"
#include <assert.h>
#include <map>

namespace vespalib {
namespace metrics {

namespace {

template<typename T>
std::vector<typename T::aggregator_type>
mergeFromSamples(const StableStore<typename T::sample_type> &source)
{
    using Aggregator = typename T::aggregator_type;
    using Sample = typename T::sample_type;
    using Map = std::map<MetricPointId, Aggregator>;
    using MapValue = typename Map::value_type;

    Map map;
    source.for_each([&map] (const Sample &sample) {
        MetricPointId id = sample.idx;
        auto iter_check = map.emplace(id, sample);
        if (!iter_check.second) {
            iter_check.first->second.merge(sample);
        }
    });
    std::vector<typename T::aggregator_type> result;
    for (const MapValue &entry : map) {
        result.push_back(entry.second);
    }
    return result;
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
            result.push_back(*a_iter);
            result.back().merge(*b_iter);
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

template<typename T>
std::vector<T>
findMissing(const std::vector<T> &already,
            const std::vector<T> &complete)
{
    std::vector<T> result;
    auto a_iter = already.begin();
    auto c_iter = complete.begin();
    while (a_iter != already.end() &&
           c_iter != complete.end())
    {
        if (a_iter->idx < c_iter->idx) {
            // missing from "complete", should not happen
            ++a_iter;
        } else if (c_iter->idx < a_iter->idx) {
            // missing this
            result.push_back(*c_iter);
            ++c_iter;
        } else {
            // already have this
            ++a_iter;
            ++c_iter;
        }
    }
    while (c_iter != complete.end()) {
        // missing this
        result.push_back(*c_iter);
        ++c_iter;
    }
    return result;
}


} // namespace <unnamed>

void Bucket::merge(const CurrentSamples &samples)
{
    counters = mergeFromSamples<Counter>(samples.counterIncrements);
    gauges = mergeFromSamples<Gauge>(samples.gaugeMeasurements);
}

void Bucket::merge(const Bucket &other)
{
    assert(genCnt < other.genCnt);
    genCnt = other.genCnt;
    startTime = std::min(startTime, other.startTime);
    endTime = std::max(endTime, other.endTime);

    std::vector<CounterAggregator> nextCounters = mergeVectors(counters, other.counters);
    counters = std::move(nextCounters);

    std::vector<GaugeAggregator> nextGauges = mergeVectors(gauges, other.gauges);
    gauges = std::move(nextGauges);
}

void Bucket::padMetrics(const Bucket &source)
{
    std::vector<CounterAggregator> missingC = findMissing(counters, source.counters);
    for (CounterAggregator aggr : missingC) {
        aggr.count = 0;
        counters.push_back(aggr);
    }
    std::vector<GaugeAggregator> missingG = findMissing(gauges, source.gauges);
    for (GaugeAggregator aggr : missingG) {
        aggr.observedCount = 0;
        aggr.sumValue = 0;
        aggr.minValue = 0;
        aggr.maxValue = 0;
        gauges.push_back(aggr);
    }
}

} // namespace vespalib::metrics
} // namespace vespalib
