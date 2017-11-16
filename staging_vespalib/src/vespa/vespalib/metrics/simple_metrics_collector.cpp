// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simple_metrics_collector.h"

namespace vespalib {
namespace metrics {

SimpleMetricsCollector::SimpleMetricsCollector(const CollectorConfig &config)
    : _counterNames(),
      _gaugeNames(),
      _currentBucket(),
      _startTime(clock::now()),
      _curTime(_startTime),
      _buckets(),
      _firstBucket(0),
      _maxBuckets(config.sliding_window_seconds)
{
    if (_maxBuckets < 1) _maxBuckets = 1;
}

std::shared_ptr<SimpleMetricsCollector>
SimpleMetricsCollector::create(const CollectorConfig &config)
{
    return std::shared_ptr<SimpleMetricsCollector>(
        new SimpleMetricsCollector(config));
}

Counter
SimpleMetricsCollector::counter(const vespalib::string &name)
{
    int id = _counterNames.resolve(name);
    return Counter(shared_from_this(), id);
}

Gauge
SimpleMetricsCollector::gauge(const vespalib::string &name)
{
    int id = _gaugeNames.resolve(name);
    return Gauge(shared_from_this(), id);
}

Snapshot
SimpleMetricsCollector::getSnapshot()
{
    Bucket merger(_curTime, _curTime);
    for (size_t i = 0; i < _buckets.size(); i++) {
        size_t off = (_firstBucket + i) % _buckets.size();
        merger.merge(_buckets[off]);
    }
    Snapshot snap;
    return snap;
}


void
SimpleMetricsCollector::collectCurrentBucket()
{
    clock::time_point prev = _curTime;
    clock::time_point curr = clock::now();

    CurrentSamples samples;
    swap(samples, _currentBucket);

    Bucket merger(prev, curr);
    if (_buckets.size() < _maxBuckets) {
        _buckets.push_back(merger);
        _buckets.back().merge(samples);
    } else {
        merger.merge(samples);
        swap(_buckets[_firstBucket], merger);
        _firstBucket = (_firstBucket + 1) % _buckets.size();
    }
    _curTime = curr;
}

} // namespace vespalib::metrics
} // namespace vespalib
