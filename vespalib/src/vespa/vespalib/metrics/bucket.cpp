// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucket.h"
#include <assert.h>
#include <map>
#include <vespa/vespalib/util/overload.h>
#include <vespa/vespalib/util/visit_ranges.h>

namespace vespalib {
namespace metrics {

namespace {

template<typename T>
std::vector<typename T::aggregator_type>
mergeFromSamples(const StableStore<typename T::sample_type> &source)
{
    using Aggregator = typename T::aggregator_type;
    using Sample = typename T::sample_type;
    using Key = std::pair<MetricId, Point>;
    using Map = std::map<Key, Aggregator>;
    using MapValue = typename Map::value_type;

    Map map;
    source.for_each([&map] (const Sample &sample) {
        Key id = sample.idx;
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
struct IdxComparator {
    bool operator() (const T& a, const T& b) { return a.idx < b.idx; }
};

template<typename T>
std::vector<T>
mergeVectors(const std::vector<T> &a,
             const std::vector<T> &b)
{
    std::vector<T> result;
    visit_ranges(overload
                 {
                     [&result](visit_ranges_either, const T& x) { result.push_back(x); },
                     [&result](visit_ranges_both, const T& x, const T& y) {
                         result.push_back(x);
                         result.back().merge(y);
                     }
                 }, a.begin(), a.end(), b.begin(), b.end(), IdxComparator<T>());
    return result;
}

template<typename T>
std::vector<T>
findMissing(const std::vector<T> &already,
            const std::vector<T> &complete)
{
    std::vector<T> result;
    visit_ranges(overload
                 {
                     // missing from "complete", should not happen:
                     [](visit_ranges_first, const T&) { },
                     // missing this:
                     [&result](visit_ranges_second, const T& x) { result.push_back(x); },
                     // already have this:
                     [](visit_ranges_both, const T&, const T&) { }
                 },
                 already.begin(), already.end(),
                 complete.begin(), complete.end(),
                 IdxComparator<T>());
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
