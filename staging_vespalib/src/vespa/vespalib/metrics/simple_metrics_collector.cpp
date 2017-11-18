// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simple_metrics_collector.h"

namespace vespalib {
namespace metrics {

SimpleMetricsCollector::SimpleMetricsCollector(const CollectorConfig &config)
    : _metricNames(),
      _axisNames(),
      _coordValues(),
      _pointMaps(),
      _currentBucket(),
      _startTime(clock::now()),
      _curTime(_startTime),
      _buckets(),
      _firstBucket(0),
      _maxBuckets(config.sliding_window_seconds),
      _stopFlag(false),
      _collectorThread(doCollectLoop, this)
{
    if (_maxBuckets < 1) _maxBuckets = 1;
    PointMap empty;
    _pointMaps.vec.push_back(empty);
    _pointMaps.map[empty] = 0;
}

SimpleMetricsCollector::~SimpleMetricsCollector()
{
    _stopFlag = true;
    _collectorThread.join();
}


std::shared_ptr<MetricsCollector>
SimpleMetricsCollector::create(const CollectorConfig &config)
{
    return std::shared_ptr<MetricsCollector>(
        new SimpleMetricsCollector(config));
}

Counter
SimpleMetricsCollector::counter(const vespalib::string &name)
{
    int id = _metricNames.resolve(name);
    return Counter(shared_from_this(), id);
}

Gauge
SimpleMetricsCollector::gauge(const vespalib::string &name)
{
    int id = _metricNames.resolve(name);
    return Gauge(shared_from_this(), id);
}

Snapshot
SimpleMetricsCollector::snapshot()
{
    clock::time_point startTime =
        (_buckets.size() > 0)
        ? _buckets[_firstBucket].startTime
        : _curTime;
    Bucket merger(startTime, startTime);
    for (size_t i = 0; i < _buckets.size(); i++) {
        size_t off = (_firstBucket + i) % _buckets.size();
        merger.merge(_buckets[off]);
    }
    auto s = merger.startTime.time_since_epoch();
    auto ss = std::chrono::duration_cast<std::chrono::microseconds>(s);
    auto e = merger.endTime.time_since_epoch();
    auto ee = std::chrono::duration_cast<std::chrono::microseconds>(e);

    Snapshot snap(ss.count() * 0.000001, ee.count() * 0.000001);
    for (const MergedCounter& entry : merger.counters) {
        size_t ni = entry.idx.name_idx;
        const vespalib::string &name = _metricNames.lookup(ni);
        CounterSnapshot val(name, entry);
        snap.add(val);
    }
    for (const MergedGauge& entry : merger.gauges) {
        size_t ni = entry.idx.name_idx;
        const vespalib::string &name = _metricNames.lookup(ni);
        GaugeSnapshot val(name, entry);
        snap.add(val);
    }
    return snap;
}

void
SimpleMetricsCollector::doCollectLoop(SimpleMetricsCollector *me)
{
    const std::chrono::milliseconds jiffy{20};
    const std::chrono::seconds oneSec{1};
    while (!me->_stopFlag) {
        std::this_thread::sleep_for(jiffy);
        clock::time_point now = clock::now();
        clock::duration elapsed = now - me->_curTime;
        if (elapsed >= oneSec) {
            me->collectCurrentBucket();
        }
    }
}

void
SimpleMetricsCollector::collectCurrentBucket()
{
    clock::time_point prev = _curTime;
    clock::time_point curr = clock::now();

    CurrentSamples samples;
    {
        std::lock_guard<std::mutex> guard(_currentBucket.lock);
        swap(samples, _currentBucket);
    }

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

Axis
SimpleMetricsCollector::axis(const vespalib::string &name)
{
    int id = _axisNames.resolve(name);
    return Axis(id);
}

Coordinate
SimpleMetricsCollector::coordinate(const vespalib::string &value)
{
    int id = _coordValues.resolve(value);
    return Coordinate(id);
}

Point
SimpleMetricsCollector::origin()
{
    return Point(shared_from_this(), 0);
}

Point
SimpleMetricsCollector::bind(const Point &point, Axis axis, Coordinate coord)
{
    PointMapBacking pm = _pointMaps.vec[point.id()].backing();
    pm.erase(axis);
    pm.insert(PointMapBacking::value_type(axis, coord));
    PointMap newMap(std::move(pm));
    auto found = _pointMaps.map.find(newMap);
    if (found != _pointMaps.map.end()) {
        size_t id = found->second;
        return Point(shared_from_this(), id);
    }
    size_t id = _pointMaps.vec.size();
    _pointMaps.vec.push_back(newMap);
    _pointMaps.map[newMap] = id;
    return Point(shared_from_this(), id);
}

} // namespace vespalib::metrics
} // namespace vespalib
