// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simple_metrics_collector.h"

#include <vespa/log/log.h>
LOG_SETUP(".simple_metrics_collector");

namespace vespalib {
namespace metrics {

SimpleMetricsCollector::SimpleMetricsCollector(const CollectorConfig &config)
    : _metricNames(),
      _axisNames(),
      _coordValues(),
      _pointMaps(),
      _currentBucket(),
      _startTime(now_stamp()),
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
LOG(info, "metric name %s -> %d", name.c_str(), id);
    return Counter(shared_from_this(), MetricIdentifier(id));
}

Gauge
SimpleMetricsCollector::gauge(const vespalib::string &name)
{
    int id = _metricNames.resolve(name);
LOG(info, "metric name %s -> %d", name.c_str(), id);
    return Gauge(shared_from_this(), MetricIdentifier(id));
}

Snapshot
SimpleMetricsCollector::snapshot()
{
    InternalTimeStamp startTime =
        (_buckets.size() > 0)
        ? _buckets[_firstBucket].startTime
        : _curTime;
    Bucket merger(startTime, startTime);
    for (size_t i = 0; i < _buckets.size(); i++) {
        size_t off = (_firstBucket + i) % _buckets.size();
        merger.merge(_buckets[off]);
    }

    std::vector<PointSnapshot> points;

    std::chrono::microseconds s = since_epoch(merger.startTime);
    std::chrono::microseconds e = since_epoch(merger.endTime);
    const double micro = 0.000001;
    Snapshot snap(s.count() * micro, e.count() * micro);
    {
        std::lock_guard<std::mutex> guard(_pointMaps.lock);
        for (const PointMap &entry : _pointMaps.vec) {
             PointSnapshot point;
             const PointMapBacking &map = entry.backing();
             for (const PointMapBacking::value_type &kv : map) {
                 point.dimensions.emplace_back(nameFor(kv.first), valueFor(kv.second));
             }
             snap.add(point);
        }
    }
    for (const MergedCounter& entry : merger.counters) {
        size_t ni = entry.idx.name_idx;
        size_t pi = entry.idx.point_idx;
        const vespalib::string &name = _metricNames.lookup(ni);
        CounterSnapshot val(name, snap.points()[pi], entry);
        snap.add(val);
    }
    for (const MergedGauge& entry : merger.gauges) {
        size_t ni = entry.idx.name_idx;
        size_t pi = entry.idx.point_idx;
        const vespalib::string &name = _metricNames.lookup(ni);
        GaugeSnapshot val(name, snap.points()[pi], entry);
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
        InternalTimeStamp now = now_stamp();
        InternalTimeStamp::duration elapsed = now - me->_curTime;
        if (elapsed >= oneSec) {
            me->collectCurrentBucket();
        }
    }
}

void
SimpleMetricsCollector::collectCurrentBucket()
{
    InternalTimeStamp prev = _curTime;
    InternalTimeStamp curr = now_stamp();

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
LOG(info, "axis name %s -> %d", name.c_str(), id);
    return Axis(id);
}

Coordinate
SimpleMetricsCollector::coordinate(const vespalib::string &value)
{
    int id = _coordValues.resolve(value);
LOG(info, "coord value %s -> %d", value.c_str(), id);
    return Coordinate(id);
}


Point
SimpleMetricsCollector::pointFrom(PointMapBacking &&map)
{
    PointMap newMap(std::move(map));
    auto found = _pointMaps.map.find(newMap);
    if (found != _pointMaps.map.end()) {
        size_t id = found->second;
LOG(info, "found point map -> %zd", id);
        return Point(id);
    }
    size_t id = _pointMaps.vec.size();
    _pointMaps.vec.push_back(newMap);
    _pointMaps.map[newMap] = id;
LOG(info, "new point map -> %zd", id);
    return Point(id);
}

} // namespace vespalib::metrics
} // namespace vespalib
