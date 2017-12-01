// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simple_metrics_manager.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.metrics.simple_metrics_manager");

namespace vespalib {
namespace metrics {

using Guard = std::lock_guard<std::mutex>;

SimpleMetricsManager::SimpleMetricsManager(const SimpleManagerConfig &config)
    : _metricNames(),
      _dimensionNames(),
      _labelValues(),
      _pointMaps(),
      _currentBucket(),
      _startTime(now_stamp()),
      _curTime(_startTime),
      _buckets(),
      _firstBucket(0),
      _maxBuckets(config.sliding_window_seconds),
      _totalsBucket(_startTime, _startTime),
      _ticker(this)
{
    if (_maxBuckets < 1) _maxBuckets = 1;
    Point empty = pointFrom(PointMap::BackingMap());
    assert(empty.id() == 0);
}

SimpleMetricsManager::~SimpleMetricsManager()
{
    _ticker.stop();
}


std::shared_ptr<MetricsManager>
SimpleMetricsManager::create(const SimpleManagerConfig &config)
{
    return std::shared_ptr<MetricsManager>(
        new SimpleMetricsManager(config));
}

Counter
SimpleMetricsManager::counter(const vespalib::string &name)
{
    size_t id = _metricNames.resolve(name);
    _metricTypes.check(id, name, MetricTypes::MetricType::COUNTER);
    LOG(debug, "counter with metric name %s -> %zd", name.c_str(), id);
    return Counter(shared_from_this(), MetricName(id));
}

Gauge
SimpleMetricsManager::gauge(const vespalib::string &name)
{
    size_t id = _metricNames.resolve(name);
    _metricTypes.check(id, name, MetricTypes::MetricType::GAUGE);
    LOG(debug, "gauge with metric name %s -> %zd", name.c_str(), id);
    return Gauge(shared_from_this(), MetricName(id));
}

Bucket
SimpleMetricsManager::mergeBuckets()
{
    Guard bucketsGuard(_bucketsLock);
    if (_buckets.size() > 0) {
        InternalTimeStamp startTime = _buckets[_firstBucket].startTime;
        Bucket merger(startTime, startTime);
        for (size_t i = 0; i < _buckets.size(); i++) {
            size_t off = (_firstBucket + i) % _buckets.size();
            merger.merge(_buckets[off]);
        }
        return merger;
    }
    // no data
    return Bucket(_startTime, _curTime);
}

Snapshot
SimpleMetricsManager::snapshotFrom(const Bucket &bucket)
{
    std::vector<PointSnapshot> points;

    std::chrono::microseconds s = since_epoch(bucket.startTime);
    std::chrono::microseconds e = since_epoch(bucket.endTime);
    const double micro = 0.000001;
    Snapshot snap(s.count() * micro, e.count() * micro);
    {
        for (size_t i = 0; i < _pointMaps.size(); ++i) {
             const PointMap::BackingMap &map = _pointMaps.lookup(i).backingMap();
             PointSnapshot point;
             for (const PointMap::BackingMap::value_type &kv : map) {
                 point.dimensions.emplace_back(nameFor(kv.first), valueFor(kv.second));
             }
             snap.add(point);
        }
    }
    for (const CounterAggregator& entry : bucket.counters) {
        size_t ni = entry.idx.name().id();
        size_t pi = entry.idx.point().id();
        const vespalib::string &name = _metricNames.lookup(ni);
        CounterSnapshot val(name, snap.points()[pi], entry);
        snap.add(val);
    }
    for (const GaugeAggregator& entry : bucket.gauges) {
        size_t ni = entry.idx.name().id();
        size_t pi = entry.idx.point().id();
        const vespalib::string &name = _metricNames.lookup(ni);
        GaugeSnapshot val(name, snap.points()[pi], entry);
        snap.add(val);
    }
    return snap;
}

Snapshot
SimpleMetricsManager::snapshot()
{
    Bucket merged = mergeBuckets();
    return snapshotFrom(merged);
}

Snapshot
SimpleMetricsManager::totalSnapshot()
{
    Guard guard(_bucketsLock);
    return snapshotFrom(_totalsBucket);
}

void
SimpleMetricsManager::collectCurrentBucket()
{
    InternalTimeStamp prev = _curTime;
    InternalTimeStamp curr = now_stamp();

    CurrentSamples samples;
    {
        Guard guard(_currentBucket.lock);
        swap(samples, _currentBucket);
    }
    Bucket newBucket(prev, curr);
    newBucket.merge(samples);

    Guard guard(_bucketsLock);
    _totalsBucket.merge(newBucket);
    if (_buckets.size() < _maxBuckets) {
        _buckets.emplace_back(std::move(newBucket));
    } else {
        _buckets[_firstBucket] = std::move(newBucket);
        _firstBucket = (_firstBucket + 1) % _buckets.size();
    }
    _curTime = curr;
}

Dimension
SimpleMetricsManager::dimension(const vespalib::string &name)
{
    size_t id = _dimensionNames.resolve(name);
    LOG(debug, "dimension name %s -> %zd", name.c_str(), id);
    return Dimension(id);
}

Label
SimpleMetricsManager::label(const vespalib::string &value)
{
    size_t id = _labelValues.resolve(value);
    LOG(debug, "label value %s -> %zd", value.c_str(), id);
    return Label(id);
}

PointBuilder
SimpleMetricsManager::pointBuilder(Point from)
{
    const PointMap &map = _pointMaps.lookup(from.id());
    return PointBuilder(shared_from_this(), map.backingMap());
}

Point
SimpleMetricsManager::pointFrom(PointMap::BackingMap map)
{
    size_t id = _pointMaps.resolve(PointMap(std::move(map)));
    return Point(id);
}

} // namespace vespalib::metrics
} // namespace vespalib
