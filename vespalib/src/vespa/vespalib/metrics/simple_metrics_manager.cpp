// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "simple_metrics_manager.h"
#include "simple_tick.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.metrics.simple_metrics_manager");

namespace vespalib {
namespace metrics {

using Guard = std::lock_guard<std::mutex>;

SimpleMetricsManager::SimpleMetricsManager(const SimpleManagerConfig &config,
                                           Tick::UP tick_supplier)
    : _currentSamples(),
      _tickSupplier(std::move(tick_supplier)),
      _startTime(_tickSupplier->first()),
      _curTime(_startTime),
      _collectCnt(0),
      _buckets(),
      _firstBucket(0),
      _maxBuckets(config.sliding_window_seconds),
      _totalsBucket(0, _startTime, _startTime),
      _thread(&SimpleMetricsManager::tickerLoop, this)
{
    if (_maxBuckets < 1) _maxBuckets = 1;
    Point empty = pointFrom(PointMap());
    assert(empty.id() == 0);
}

SimpleMetricsManager::~SimpleMetricsManager()
{
    stopThread();
}

std::shared_ptr<MetricsManager>
SimpleMetricsManager::create(const SimpleManagerConfig &config)
{
    return std::shared_ptr<MetricsManager>(
            new SimpleMetricsManager(config, std::make_unique<SimpleTick>()));
}

std::shared_ptr<MetricsManager>
SimpleMetricsManager::createForTest(const SimpleManagerConfig &config,
                                    Tick::UP tick_supplier)
{
    return std::shared_ptr<MetricsManager>(
            new SimpleMetricsManager(config, std::move(tick_supplier)));
}

Counter
SimpleMetricsManager::counter(const vespalib::string &name, const vespalib::string &)
{
    MetricId mi = MetricId::from_name(name);
    _metricTypes.check(mi.id(), name, MetricTypes::MetricType::COUNTER);
    LOG(debug, "counter with metric name %s -> %zu", name.c_str(), mi.id());
    return Counter(shared_from_this(), mi);
}

Gauge
SimpleMetricsManager::gauge(const vespalib::string &name, const vespalib::string &)
{
    MetricId mi = MetricId::from_name(name);
    _metricTypes.check(mi.id(), name, MetricTypes::MetricType::GAUGE);
    LOG(debug, "gauge with metric name %s -> %zu", name.c_str(), mi.id());
    return Gauge(shared_from_this(), mi);
}

Bucket
SimpleMetricsManager::mergeBuckets()
{
    Guard bucketsGuard(_bucketsLock);
    if (_buckets.size() > 0) {
        TimeStamp startTime = _buckets[_firstBucket].startTime;
        Bucket merger(0, startTime, startTime);
        for (size_t i = 0; i < _buckets.size(); i++) {
            size_t off = (_firstBucket + i) % _buckets.size();
            merger.merge(_buckets[off]);
        }
        merger.padMetrics(_totalsBucket);
        return merger;
    }
    // no data
    return Bucket(0, _startTime, _curTime);
}

Bucket
SimpleMetricsManager::totalsBucket()
{
    Guard bucketsGuard(_bucketsLock);
    return _totalsBucket;
}

Snapshot
SimpleMetricsManager::snapshotFrom(const Bucket &bucket)
{
    std::vector<PointSnapshot> points;

    double s = bucket.startTime.count();
    double e = bucket.endTime.count();

    size_t max_point_id = 0;
    for (const CounterAggregator& entry : bucket.counters) {
        Point p = entry.idx.second;
        max_point_id = std::max(max_point_id, p.id());
    }
    for (const GaugeAggregator& entry : bucket.gauges) {
        Point p = entry.idx.second;
        max_point_id = std::max(max_point_id, p.id());
    }
    Snapshot snap(s, e);
    {
        for (size_t point_id = 0; point_id <= max_point_id; ++point_id) {
            const PointMap &map = Point(point_id).as_map();
            PointSnapshot point;
            for (const PointMap::value_type &kv : map) {
                point.dimensions.emplace_back(kv.first.as_name(), kv.second.as_value());
            }
            snap.add(point);
        }
    }
    for (const CounterAggregator& entry : bucket.counters) {
        MetricId mi = entry.idx.first;
        Point p = entry.idx.second;
        size_t pi = p.id();
        const vespalib::string &name = mi.as_name();
        CounterSnapshot val(name, snap.points()[pi], entry);
        snap.add(val);
    }
    for (const GaugeAggregator& entry : bucket.gauges) {
        MetricId mi = entry.idx.first;
        Point p = entry.idx.second;
        size_t pi = p.id();
        const vespalib::string &name = mi.as_name();
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
    Bucket totals = totalsBucket();
    return snapshotFrom(totals);
}

void
SimpleMetricsManager::collectCurrentSamples(TimeStamp prev,
                                            TimeStamp curr)
{
    CurrentSamples samples;
    _currentSamples.extract(samples);
    Bucket newBucket(++_collectCnt, prev, curr);
    newBucket.merge(samples);

    Guard guard(_bucketsLock);
    _totalsBucket.merge(newBucket);
    if (_buckets.size() < _maxBuckets) {
        _buckets.push_back(std::move(newBucket));
    } else {
        _buckets[_firstBucket] = std::move(newBucket);
        _firstBucket = (_firstBucket + 1) % _buckets.size();
    }
}

Dimension
SimpleMetricsManager::dimension(const vespalib::string &name)
{
    Dimension dim = Dimension::from_name(name);
    LOG(debug, "dimension name %s -> %zu", name.c_str(), dim.id());
    return dim;
}

Label
SimpleMetricsManager::label(const vespalib::string &value)
{
    Label l = Label::from_value(value);
    LOG(debug, "label value %s -> %zu", value.c_str(), l.id());
    return l;
}

PointBuilder
SimpleMetricsManager::pointBuilder(Point from)
{
    const PointMap &map = from.as_map();
    return PointBuilder(shared_from_this(), map);
}

Point
SimpleMetricsManager::pointFrom(PointMap map)
{
    return Point::from_map(std::move(map));
}

void
SimpleMetricsManager::tickerLoop()
{
    while (_tickSupplier->alive()) {
        TimeStamp now = _tickSupplier->next(_curTime);
        if (_tickSupplier->alive()) {
            tick(now);
        }
    }
}

void
SimpleMetricsManager::stopThread()
{
    _tickSupplier->kill();
    _thread.join();
}

void
SimpleMetricsManager::tick(TimeStamp now)
{
    TimeStamp prev = _curTime;
    collectCurrentSamples(prev, now);
    _curTime = now;
}

} // namespace vespalib::metrics
} // namespace vespalib
